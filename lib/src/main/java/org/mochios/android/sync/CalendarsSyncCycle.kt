// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.sync

import java.util.UUID

/**
 * One sync of the account's calendars: bring the calendars themselves into
 * line, download what changed on the server, then upload what changed on the
 * phone.
 *
 * 0. **Calendars.** Every calendar the server holds becomes a provider
 *    calendar; one the server no longer has is removed with its events. The
 *    per-calendar sync toggle is the user's, so a calendar switched off keeps
 *    no events, and one switched on since the last run makes the next
 *    download a full listing rather than leaving it empty until one of its
 *    events happens to change.
 * 1. **Download.** Changes since the stored cursor; the changed ids fetched
 *    in batches and written as new or replaced rows, skipping any event the
 *    phone has changed, which the upload step resolves; events the server
 *    deleted are removed; the new cursor stored. A reset listing names every
 *    event the server holds, so a clean one it leaves out is removed too.
 * 2. **Upload.** An event made on the phone is created; a changed one is
 *    read back from the server first, so the properties the phone has no
 *    column for survive the edit, and then updated with the phone's etag; a
 *    deleted one is deleted with it. Success stores the server's id and etag
 *    and clears the flags.
 * 3. **Conflict.** A server copy that has moved on since the phone's — an
 *    etag that no longer matches, or a 412 from the write itself — means the
 *    server wins: the event is re-downloaded and the phone's edit dropped. A
 *    local delete against a changed event is not applied; it comes back.
 * 4. **Retry.** A transport failure propagates with the dirty flags still
 *    set, so the framework backs off and retries; so does an authorization
 *    failure, which the adapter reports as hard.
 * 5. **Lost answer.** An event is named (`android-<uuid>`, stored on its
 *    master row) before its first create, and every create of it carries the
 *    name, so a create sent again gets the event an earlier attempt made
 *    rather than a second. An event the download finds under the name of a
 *    row not yet bound is that one: the rows are bound to it instead of a
 *    copy being added, and the upload resolves them as any bound event.
 *
 * Nothing here touches Android, so the cycle runs against fakes in tests.
 */
class CalendarsSyncCycle(
    private val source: CalendarsSource,
    private val store: CalendarsStore,
) {

    /**
     * The rows of one event: its master first, then its overrides. A group is
     * what the server holds as a single event, whatever the phone splits it
     * into.
     */
    private data class Group(val rows: List<StoredEvent>) {
        val master: StoredEvent get() = rows.first()

        /** The server's id for the event, null for one made on the phone. */
        val remote: String?
            get() = rows.firstNotNullOfOrNull { it.remote } ?: rows.firstNotNullOfOrNull { it.original }

        val etag: String get() = rows.firstNotNullOfOrNull { it.etag }.orEmpty()

        val calendar: Long get() = master.calendar

        val slug: String? get() = master.slug

        /** Whether the phone has changed the event since the last sync. */
        val changed: Boolean get() = rows.any { it.dirty || it.deleted }

        /** Whether the phone has deleted the event outright. */
        val gone: Boolean get() = master.deleted || rows.all { it.deleted }
    }

    suspend fun run(): SyncOutcome {
        var downloaded = 0
        var uploaded = 0
        var removed = 0
        val failures = mutableListOf<String>()

        // ---- 0. Calendars ----
        val served = source.calendars()
        val names = served.associateBy { it.id }
        for (calendar in store.calendars()) {
            if (calendar.remote !in names) store.remove(calendar)
        }
        val local = mutableMapOf<String, Long>()
        for (calendar in served) local[calendar.id] = store.calendar(calendar)
        val enabled = store.calendars().filter { it.sync }.map { it.remote }.toSet()
        // A calendar the phone may not write to takes no uploads, and the
        // server would refuse them anyway.
        val writable = served.filter { !it.readonly }.map { it.id }.toSet()

        // ---- 1. Download ----
        val cursor = store.cursor()
        // A calendar switched on since the last run holds none of its events,
        // so the cursor is worth nothing for it and the listing starts over.
        val whole = cursor.version == 0L || !cursor.calendars.containsAll(enabled)
        val since = if (whole) 0L else cursor.version
        val changes = source.changes(since)

        val groups = groups(store.events())
        val bound = groups.filter { it.remote != null }.associateBy { it.remote!! }
        // Events made on the phone and named but never bound, by name.
        val unbound = groups.filter { it.remote == null && it.slug != null }.associateBy { it.slug!! }
        // Events the download bound such rows to, by master row.
        val adopted = mutableMapOf<Long, SyncedEvent>()

        for (id in changes.deleted) {
            val group = bound[id] ?: continue
            // The server's delete wins over a phone edit not yet uploaded, as
            // a 412 would have; the edit had nowhere to go.
            store.remove(group.rows)
            removed++
        }

        val changed = changes.changed.toSet()
        val deleted = changes.deleted.toSet()
        // A server that predates the reset flag still lists everything for a
        // cursor of 0, so a first sync is a reset whichever server answers.
        if (changes.reset || since == 0L) {
            for (group in groups) {
                val remote = group.remote ?: continue
                if (remote in changed || remote in deleted || group.changed) continue
                store.remove(group.rows)
                removed++
            }
        }

        val wanted = changes.changed.filter { id -> bound[id]?.changed != true }
        for (batch in wanted.chunked(CalendarsSource.BATCH)) {
            for (event in source.fetch(batch)) {
                val calendar = local[event.calendar]
                val group = bound[event.id]
                if (calendar == null || event.calendar !in enabled) {
                    // The event moved into a calendar the phone does not
                    // carry: its rows go, and come back if it is switched on.
                    if (group != null) {
                        store.remove(group.rows)
                        removed++
                    }
                    continue
                }
                val mine = if (group == null) event.slug?.let { unbound[it] } else null
                if (mine != null) {
                    // ---- 5. Lost answer: a create of this event made it. ----
                    // Bound, still dirty, for the upload to resolve.
                    store.bind(mine.rows, event, settled = false)
                    adopted[mine.master.local] = event
                    continue
                }
                when {
                    group == null -> store.write(calendar, event)
                    group.etag != event.etag -> store.write(calendar, event, group.rows)
                    else -> continue
                }
                downloaded++
            }
        }
        store.cursor(SyncCursor(changes.version, enabled))

        // ---- 2. Upload ----
        for (group in groups.filter { it.changed }) {
            val found = adopted[group.master.local]
            val remote = found?.id ?: group.remote
            val etag = found?.etag ?: group.etag
            if (group.gone) {
                if (remote == null) {
                    // Created and deleted on the phone between syncs.
                    store.remove(group.rows)
                    removed++
                    continue
                }
                if (remote in deleted) continue // already removed above
                try {
                    source.delete(remote, etag)
                    store.remove(group.rows)
                    removed++
                } catch (_: SyncConflict) {
                    // ---- 3. Conflict: the server's event comes back. ----
                    if (restore(group, remote, local)) downloaded++ else removed++
                } catch (_: SyncMissing) {
                    store.remove(group.rows)
                    removed++
                } catch (e: SyncRefused) {
                    failures.add(e.message.orEmpty())
                }
                continue
            }
            if (remote in deleted) continue
            val calendar = store.calendars().firstOrNull { it.local == group.calendar }
            if (calendar == null || calendar.remote !in writable) {
                // A read-only calendar: the phone's edit has nowhere to go,
                // so the server's copy comes back over it.
                if (remote != null && restore(group, remote, local)) downloaded++
                continue
            }
            try {
                // The provider keeps no version on an event, so an edit made
                // during the upload is caught by reading the rows back: the
                // flags clear only while they still say what went up.
                val before = store.rows(group.rows)
                val saved = send(group, remote, etag, calendar.remote, before)
                store.bind(group.rows, saved, settled = store.rows(group.rows) == before)
                uploaded++
            } catch (e: SyncConflict) {
                // A create has no etag to be stale against; a 412 on one is
                // the server refusing it, not a conflict to resolve.
                if (remote == null) {
                    failures.add(e.message.orEmpty())
                } else if (restore(group, remote, local)) {
                    downloaded++
                } else {
                    removed++
                }
            } catch (e: SyncMissing) {
                // Gone from the server: the server wins over a phone edit. An
                // event made on the phone was never there, so a 404 on its
                // create is a refusal and the rows stay for the next attempt.
                if (remote == null) {
                    failures.add(e.message.orEmpty())
                } else {
                    store.remove(group.rows)
                    removed++
                }
            } catch (e: SyncRefused) {
                failures.add(e.message.orEmpty())
            }
        }
        return SyncOutcome(downloaded, uploaded, removed, failures)
    }

    /**
     * Sends one event up. A create carries the phone's rows alone; an update
     * reads the server's copy first, so the properties the phone has no
     * column for — an organiser, a class, a desktop client's own — survive
     * the edit, and a copy that has already moved on is a conflict before the
     * write rather than after it.
     */
    private suspend fun send(
        group: Group,
        remote: String?,
        etag: String,
        calendar: String,
        rows: List<EventRow>,
    ): SyncedEvent {
        if (remote == null) {
            val slug = group.slug ?: name(group.master.local)
            return source.create(calendar, CalendarsMapping.components(rows), slug)
        }
        val current = source.fetch(listOf(remote)).firstOrNull() ?: throw SyncMissing()
        if (current.etag != etag) throw SyncConflict()
        val move = if (current.calendar == calendar) null else calendar
        return source.update(remote, etag, move, CalendarsMapping.components(rows, current.components))
    }

    /**
     * Names an event made on the phone and stores the name on its master row
     * before the create that carries it goes out, so a retry sends the same
     * one.
     */
    private fun name(local: Long): String {
        val slug = "android-${UUID.randomUUID()}"
        store.slug(local, slug)
        return slug
    }

    /**
     * Re-downloads [remote] over the phone's rows, dropping the local edit or
     * delete. The rows are written afresh rather than undeleted in place, so
     * a deleted event rejoins the phone's calendar as any new one does.
     * Returns false when the server no longer has it either, in which case
     * the rows are removed.
     */
    private suspend fun restore(group: Group, remote: String, local: Map<String, Long>): Boolean {
        val fresh = source.fetch(listOf(remote)).firstOrNull()
        val calendar = fresh?.let { local[it.calendar] }
        if (fresh == null || calendar == null) {
            store.remove(group.rows)
            return false
        }
        store.write(calendar, fresh, group.rows)
        return true
    }

    /**
     * The account's rows gathered into events: by the server's id where there
     * is one, else by the master an override names, else one event per row.
     */
    private fun groups(rows: List<StoredEvent>): List<Group> {
        val byLocal = rows.associateBy { it.local }
        val out = linkedMapOf<String, MutableList<StoredEvent>>()
        for (row in rows) out.getOrPut(key(row, byLocal)) { mutableListOf() }.add(row)
        return out.values.map { group -> Group(group.sortedBy { if (it.exception()) 1 else 0 }) }
    }

    private fun key(row: StoredEvent, byLocal: Map<Long, StoredEvent>, depth: Int = 0): String = when {
        row.remote != null -> row.remote
        row.original != null -> row.original
        // An override of an event made on the phone names its master's row.
        row.origin != 0L && depth < DEPTH ->
            byLocal[row.origin]?.let { key(it, byLocal, depth + 1) } ?: "local:${row.local}"
        else -> "local:${row.local}"
    }

    private companion object {
        /** A master names no master, so one hop is enough; the guard is for a cycle. */
        const val DEPTH = 4
    }
}
