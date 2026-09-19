// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.sync

import java.util.UUID

/**
 * What one run did. [failures] are rows the server refused for a reason a
 * retry will not fix — a card it will not accept — with the server's message;
 * they are reported and skipped rather than blocking the rest.
 */
data class SyncOutcome(
    val downloaded: Int = 0,
    val uploaded: Int = 0,
    val removed: Int = 0,
    val failures: List<String> = emptyList(),
)

/** A row the server refused with a client error other than 412 or 404. */
class SyncRefused(message: String?) : Exception(message ?: "refused")

/**
 * One sync of the account's contacts: download what changed on the server,
 * then upload what changed on the phone.
 *
 * 1. **Download.** Changes since the stored cursor; the changed ids fetched in
 *    batches and written as new or replaced rows, skipping any row that is
 *    locally dirty or deleted, which the upload step resolves; rows the server
 *    deleted are removed; the new cursor stored. A reset listing names every
 *    contact the server holds, so a clean row it leaves out is removed too.
 * 2. **Upload.** Dirty rows without a server id are created; dirty rows with
 *    one are updated with their etag; deleted rows are deleted with theirs.
 *    Success stores the server's id, etag and book and clears the flag.
 * 3. **Conflict.** A 412 means the server moved on: the server wins, the row
 *    is re-downloaded and the phone's edit dropped. A local delete against a
 *    changed row is not applied; the row comes back.
 * 4. **Retry.** A transport failure propagates with the dirty flags still
 *    set, so the framework backs off and retries; so does an authorization
 *    failure, which the adapter reports as hard.
 * 5. **Lost answer.** A row is named (`android-<uuid>`, stored on the row)
 *    before its first create, and every create of it carries the name, so a
 *    create sent again gets the contact an earlier attempt made rather than a
 *    second. A contact the download finds under the name of a row not yet
 *    bound is that one: the row is bound to it instead of a copy being added,
 *    and the upload resolves it as any bound row. A delete made on the phone
 *    meanwhile goes up as a delete; a card changed since goes up as an update.
 *
 * Nothing here touches Android, so the cycle runs against fakes in tests.
 */
class ContactsSyncCycle(
    private val source: ContactsSource,
    private val store: ContactsStore,
) {

    suspend fun run(): SyncOutcome {
        var downloaded = 0
        var uploaded = 0
        var removed = 0
        val failures = mutableListOf<String>()

        // ---- 1. Download ----
        val since = store.version()
        val changes = source.changes(since)
        val index = store.contacts()
        val byRemote = index.filter { it.remote != null }.associateBy { it.remote!! }
        // Rows made on the phone and named but never bound, by name.
        val unbound = index.filter { it.remote == null && it.slug != null }.associateBy { it.slug!! }
        // Contacts the download bound such rows to, by row.
        val adopted = mutableMapOf<Long, SyncedContact>()

        for (id in changes.deleted) {
            val local = byRemote[id] ?: continue
            // The server's delete wins over a phone edit not yet uploaded, as
            // a 412 would have; the edit had nowhere to go.
            store.remove(local.local)
            removed++
        }

        val changed = changes.changed.toSet()
        val deleted = changes.deleted.toSet()
        // A server that predates the reset flag still lists everything for a
        // cursor of 0, so a first sync is a reset whichever server answers.
        if (changes.reset || since == 0L) {
            // Every contact the server holds: a row bound to an id it leaves
            // out was deleted there unseen, while sync was off or longer ago
            // than the server remembers deletions. A row with a phone change
            // pending is left to the upload step, which resolves it.
            for (row in index) {
                val remote = row.remote ?: continue
                if (remote in changed || remote in deleted || row.dirty || row.deleted) continue
                store.remove(row.local)
                removed++
            }
        }

        val wanted = changes.changed.filter { id ->
            val local = byRemote[id]
            local == null || !(local.dirty || local.deleted)
        }
        for (batch in wanted.chunked(ContactsSource.BATCH)) {
            for (contact in source.fetch(batch)) {
                val local = byRemote[contact.id]
                val mine = if (local == null) contact.slug?.let { unbound[it] } else null
                if (mine != null) {
                    // ---- 5. Lost answer: a create of this row made it. ----
                    // Bound, still dirty or deleted, for the upload to resolve.
                    store.bind(mine.local, contact, null)
                    adopted[mine.local] = contact
                    continue
                }
                when {
                    local == null -> store.insert(contact)
                    local.etag != contact.etag -> store.replace(local.local, contact)
                    else -> continue
                }
                downloaded++
            }
        }
        store.version(changes.version)

        // ---- 2. Upload ----
        for (listed in index.filter { it.dirty || it.deleted }) {
            val found = adopted[listed.local]
            val row = found?.let { listed.copy(remote = it.id, etag = it.etag, book = it.book) } ?: listed
            val remote = row.remote
            if (row.deleted) {
                if (remote == null) {
                    // Created and deleted on the phone between syncs.
                    store.remove(row.local)
                    removed++
                    continue
                }
                if (remote in deleted) continue // already removed above
                try {
                    source.delete(remote, row.etag.orEmpty())
                    store.remove(row.local)
                    removed++
                } catch (_: SyncConflict) {
                    // ---- 3. Conflict: the server's row comes back. ----
                    if (restore(row, remote)) downloaded++ else removed++
                } catch (_: SyncMissing) {
                    store.remove(row.local)
                    removed++
                } catch (e: SyncRefused) {
                    failures.add(e.message.orEmpty())
                }
                continue
            }
            if (remote in deleted) continue
            val properties = store.properties(row.local)
            try {
                val saved = when {
                    remote == null -> source.create(properties, row.slug ?: name(row.local))
                    // The card the lost create carried is still the phone's.
                    found != null && found.properties == properties -> found
                    else -> source.update(remote, row.etag.orEmpty(), properties)
                }
                store.bind(row.local, saved, row.version)
                uploaded++
            } catch (e: SyncConflict) {
                // A create has no etag to be stale against; a 412 on one is
                // the server refusing it, not a conflict to resolve.
                if (remote == null) {
                    failures.add(e.message.orEmpty())
                } else if (restore(row, remote)) {
                    downloaded++
                } else {
                    removed++
                }
            } catch (e: SyncMissing) {
                // Gone from the server: the server wins over a phone edit. A
                // contact made on the phone was never there, so a 404 on its
                // create is a refusal and the row stays for the next attempt.
                if (remote == null) {
                    failures.add(e.message.orEmpty())
                } else {
                    store.remove(row.local)
                    removed++
                }
            } catch (e: SyncRefused) {
                failures.add(e.message.orEmpty())
            }
        }
        return SyncOutcome(downloaded, uploaded, removed, failures)
    }

    /**
     * Names a contact made on the phone and stores the name on its row before
     * the create that carries it goes out, so a retry sends the same one.
     */
    private fun name(local: Long): String {
        val slug = "android-${UUID.randomUUID()}"
        store.slug(local, slug)
        return slug
    }

    /**
     * Re-downloads [remote] over the phone's row, dropping the local edit or
     * delete. A deleted row is written afresh rather than undeleted in place,
     * so it rejoins the phone's contacts as any new one does. Returns false
     * when the server no longer has it either, in which case the row is
     * removed.
     */
    private suspend fun restore(row: StoredContact, remote: String): Boolean {
        val fresh = source.fetch(listOf(remote)).firstOrNull()
        if (fresh == null) {
            store.remove(row.local)
            return false
        }
        if (row.deleted) {
            store.recreate(row.local, fresh)
        } else {
            store.replace(row.local, fresh)
        }
        return true
    }
}
