// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * The calendar sync cycle against a fake server and a fake provider:
 * calendars, download, upload, conflict and retry, each as the plan states
 * them.
 */
class CalendarsSyncCycleTest {

    private fun event(summary: String) = listOf(
        EventComponent("VEVENT", listOf(property("SUMMARY", summary))),
    )

    /** The server: calendars, events by id, a change log and a script of failures. */
    private class FakeSource : CalendarsSource {
        val calendars = linkedMapOf(
            "calendar-1" to SyncedCalendar("calendar-1", "Calendar", "#60a5fa", default = true),
        )
        val events = linkedMapOf<String, SyncedEvent>()
        val deleted = mutableListOf<String>()
        var version = 1L
        var next = 1
        var refuse: String? = null
        var offline = false
        var unauthorized = false
        /** The next update or delete finds the event gone mid-flight. */
        var vanish = false
        /** The cursor predates deletions the server no longer remembers. */
        var pruned = false
        /** How many creates yet to commit and then lose their answer. */
        var lose = 0
        /** The name each create carried, in order. */
        val slugs = mutableListOf<String>()
        val calls = mutableListOf<String>()

        fun seed(id: String, summary: String, calendar: String = "calendar-1", etag: String = "e-$id"): SyncedEvent {
            val saved = SyncedEvent(id, calendar, etag, listOf(EventComponent("VEVENT", listOf(property("SUMMARY", summary)))), id)
            events[id] = saved
            return saved
        }

        private fun gate() {
            if (offline) throw IOException("offline")
            if (unauthorized) throw SyncAuthorization()
        }

        override suspend fun calendars(): List<SyncedCalendar> {
            gate()
            calls.add("calendars")
            return calendars.values.toList()
        }

        override suspend fun changes(since: Long): CalendarsChanges {
            gate()
            calls.add("changes:$since")
            val reset = since == 0L || pruned
            return CalendarsChanges(version, events.keys.toList(), if (reset) emptyList() else deleted.toList(), reset)
        }

        override suspend fun fetch(ids: List<String>): List<SyncedEvent> {
            gate()
            calls.add("fetch:${ids.joinToString(",")}")
            return ids.mapNotNull { events[it] }
        }

        override suspend fun create(calendar: String, components: List<EventComponent>, slug: String): SyncedEvent {
            gate()
            calls.add("create:$calendar")
            slugs.add(slug)
            refuse?.let { throw SyncRefused(it) }
            var saved = events.values.firstOrNull { it.slug == slug }
            if (saved == null) {
                val id = "new${next++}"
                saved = SyncedEvent(id, calendar, "e-$id-1", components, slug)
                events[id] = saved
                version++
            }
            if (lose > 0) {
                lose--
                throw IOException("answer lost")
            }
            return saved
        }

        override suspend fun update(
            id: String,
            etag: String,
            calendar: String?,
            components: List<EventComponent>,
        ): SyncedEvent {
            gate()
            calls.add("update:$id")
            val current = events[id] ?: throw SyncMissing()
            if (vanish) {
                events.remove(id)
                throw SyncConflict()
            }
            if (current.etag != etag) throw SyncConflict()
            val saved = current.copy(etag = etag + "'", components = components, calendar = calendar ?: current.calendar)
            events[id] = saved
            version++
            return saved
        }

        override suspend fun delete(id: String, etag: String) {
            gate()
            calls.add("delete:$id")
            val current = events[id] ?: throw SyncMissing()
            if (current.etag != etag) throw SyncConflict()
            events.remove(id)
            deleted.add(id)
            version++
        }
    }

    /** The provider: calendars and event rows in memory, with their flags. */
    private class FakeStore : CalendarsStore {
        var cursor = SyncCursor()
        val calendars = linkedMapOf<Long, StoredCalendar>()
        val rows = linkedMapOf<Long, StoredEvent>()
        val components = linkedMapOf<Long, List<EventComponent>>()
        var next = 1L
        /** The phone edits the row again while the upload is in flight. */
        var churn = false
        private var reads = 0

        override fun cursor(): SyncCursor = cursor

        override fun cursor(value: SyncCursor) {
            cursor = value
        }

        override fun calendars(): List<StoredCalendar> = calendars.values.toList()

        override fun calendar(calendar: SyncedCalendar): Long {
            val existing = calendars.values.firstOrNull { it.remote == calendar.id }
            if (existing != null) {
                calendars[existing.local] = existing.copy(
                    name = calendar.name,
                    colour = calendar.colour,
                    readonly = calendar.readonly,
                )
                return existing.local
            }
            val local = next++
            calendars[local] = StoredCalendar(local, calendar.id, calendar.name, calendar.colour, calendar.readonly)
            return local
        }

        override fun remove(calendar: StoredCalendar) {
            calendars.remove(calendar.local)
            for (row in rows.values.filter { it.calendar == calendar.local }) rows.remove(row.local)
        }

        override fun events(): List<StoredEvent> =
            rows.values.filter { row -> calendars[row.calendar]?.sync != false }

        override fun rows(group: List<StoredEvent>): List<EventRow> {
            if (churn) reads++
            return group.map { row ->
                EventRow(
                    values = mapOf(
                        android.provider.CalendarContract.Events._SYNC_ID to row.name,
                        android.provider.CalendarContract.Events.ORIGINAL_SYNC_ID to row.original,
                        android.provider.CalendarContract.Events.TITLE to "edit $reads",
                    ),
                    deleted = row.deleted,
                )
            }
        }

        override fun write(calendar: Long, event: SyncedEvent, replacing: List<StoredEvent>) {
            for (row in replacing) {
                rows.remove(row.local)
                components.remove(row.local)
            }
            val local = next++
            rows[local] = StoredEvent(
                local = local,
                calendar = calendar,
                remote = event.id,
                name = event.id,
                etag = event.etag,
                slug = event.slug,
            )
            components[local] = event.components
        }

        override fun bind(group: List<StoredEvent>, event: SyncedEvent, settled: Boolean) {
            for (row in group) {
                rows[row.local] = row.copy(
                    remote = event.id,
                    name = if (row.exception()) row.name else event.id,
                    etag = event.etag,
                    slug = event.slug,
                    dirty = if (settled) false else row.dirty,
                )
            }
        }

        override fun slug(local: Long, value: String) {
            rows[local]?.let { rows[local] = it.copy(slug = value) }
        }

        override fun remove(group: List<StoredEvent>) {
            for (row in group) {
                rows.remove(row.local)
                components.remove(row.local)
            }
        }

        /** A row made on the phone, as the phone's calendar app would leave it. */
        fun make(
            calendar: Long,
            remote: String? = null,
            etag: String? = null,
            dirty: Boolean = false,
            deleted: Boolean = false,
            original: String? = null,
            slug: String? = null,
        ): StoredEvent {
            val local = next++
            val row = StoredEvent(
                local = local,
                calendar = calendar,
                remote = remote,
                name = remote,
                etag = etag,
                original = original,
                dirty = dirty,
                deleted = deleted,
                slug = slug,
            )
            rows[local] = row
            return row
        }
    }

    private fun run(source: FakeSource, store: FakeStore) =
        runBlocking { CalendarsSyncCycle(source, store).run() }

    // ---- 0. Calendars ----

    @Test
    fun `the server's calendars become the phone's`() {
        val source = FakeSource()
        source.calendars["calendar-2"] = SyncedCalendar("calendar-2", "Birthdays", "#f472b6", "birthdays", readonly = true)
        val store = FakeStore()
        run(source, store)
        assertEquals(listOf("calendar-1", "calendar-2"), store.calendars().map { it.remote })
        assertTrue(store.calendars().first { it.remote == "calendar-2" }.readonly)
    }

    @Test
    fun `a calendar the server no longer has goes, with its events`() {
        val source = FakeSource()
        source.calendars["calendar-2"] = SyncedCalendar("calendar-2", "Gone")
        val store = FakeStore()
        run(source, store)
        val gone = store.calendars().first { it.remote == "calendar-2" }
        store.make(gone.local, remote = "orphan", etag = "e")
        source.calendars.remove("calendar-2")
        run(source, store)
        assertNull(store.calendars().firstOrNull { it.remote == "calendar-2" })
        assertTrue(store.rows.values.none { it.remote == "orphan" })
    }

    @Test
    fun `a calendar switched on since the last run makes the next listing whole`() {
        val source = FakeSource()
        source.calendars["calendar-2"] = SyncedCalendar("calendar-2", "Later")
        val store = FakeStore()
        run(source, store)
        // The user turns the second calendar off, then on again.
        val second = store.calendars().first { it.remote == "calendar-2" }
        store.calendars[second.local] = second.copy(sync = false)
        run(source, store)
        assertEquals(setOf("calendar-1"), store.cursor.calendars)
        store.calendars[second.local] = second.copy(sync = true)
        source.calls.clear()
        run(source, store)
        assertTrue("the cursor no longer covers it, so the listing starts over", "changes:0" in source.calls)
    }

    // ---- 1. Download ----

    @Test
    fun `a first sync downloads everything and stores the cursor`() {
        val source = FakeSource()
        source.seed("a", "Stand-up")
        source.seed("b", "Retro")
        val store = FakeStore()
        val outcome = run(source, store)
        assertEquals(2, outcome.downloaded)
        assertEquals(setOf("a", "b"), store.rows.values.mapNotNull { it.remote }.toSet())
        assertEquals(source.version, store.cursor.version)
    }

    @Test
    fun `a changed event replaces the phone's rows, an unchanged one is left alone`() {
        val source = FakeSource()
        source.seed("a", "Stand-up")
        val store = FakeStore()
        run(source, store)
        val before = store.rows.keys.single()
        run(source, store)
        assertEquals("unchanged etag, so the row stays", before, store.rows.keys.single())
        source.events["a"] = source.events["a"]!!.copy(etag = "e-a-2")
        val outcome = run(source, store)
        assertEquals(1, outcome.downloaded)
        assertEquals("e-a-2", store.rows.values.single().etag)
    }

    @Test
    fun `an event the server deleted is removed from the phone`() {
        val source = FakeSource()
        source.seed("a", "Stand-up")
        val store = FakeStore()
        run(source, store)
        source.events.remove("a")
        source.deleted.add("a")
        val outcome = run(source, store)
        assertEquals(1, outcome.removed)
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun `a reset listing takes away a clean row it leaves out`() {
        val source = FakeSource()
        source.seed("a", "Stand-up")
        val store = FakeStore()
        run(source, store)
        // Gone while sync was off, and longer ago than the server remembers.
        source.events.remove("a")
        source.pruned = true
        val outcome = run(source, store)
        assertEquals(1, outcome.removed)
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun `a dirty row is left for the upload rather than overwritten`() {
        val source = FakeSource()
        source.seed("a", "Stand-up")
        val store = FakeStore()
        run(source, store)
        val row = store.rows.values.single()
        store.rows[row.local] = row.copy(dirty = true)
        source.events["a"] = source.events["a"]!!.copy(etag = "e-a-2")
        source.calls.clear()
        val outcome = run(source, store)
        // The download leaves the dirty row alone and the upload resolves it:
        // the server has moved on, so its copy replaces the phone's edit.
        assertEquals(0, outcome.uploaded)
        assertEquals(1, outcome.downloaded)
        assertEquals("e-a-2", store.rows.values.single().etag)
        assertFalse(store.rows.values.single().dirty)
    }

    // ---- 2. Upload ----

    @Test
    fun `an event made on the phone is created and bound`() {
        val source = FakeSource()
        val store = FakeStore()
        run(source, store)
        val calendar = store.calendars().single()
        store.make(calendar.local, dirty = true)
        val outcome = run(source, store)
        assertEquals(1, outcome.uploaded)
        assertEquals(1, source.events.size)
        val row = store.rows.values.single()
        assertEquals(source.events.keys.single(), row.remote)
        assertFalse(row.dirty)
    }

    @Test
    fun `a create carries the name the row was given, so a retry finds the same event`() {
        val source = FakeSource()
        val store = FakeStore()
        run(source, store)
        val calendar = store.calendars().single()
        store.make(calendar.local, dirty = true)
        source.lose = 1
        try {
            run(source, store)
            fail("the lost answer is a transport failure the framework retries")
        } catch (_: IOException) {
            // as it should
        }
        // The answer was lost, so the row is still dirty and unbound.
        assertNull(store.rows.values.single().remote)
        val slug = store.rows.values.single().slug
        assertNotNull(slug)
        run(source, store)
        // The next run finds the event under the name the lost create gave
        // it, binds the row to it and resolves the row as any bound one:
        // one event, and no second create.
        assertEquals("one event, not two", 1, source.events.size)
        assertEquals(listOf(slug), source.slugs)
        assertEquals(source.events.keys.single(), store.rows.values.single().remote)
        assertFalse(store.rows.values.single().dirty)
    }

    @Test
    fun `a dirty row goes up as an update with the etag the phone holds`() {
        val source = FakeSource()
        source.seed("a", "Stand-up")
        val store = FakeStore()
        run(source, store)
        val row = store.rows.values.single()
        store.rows[row.local] = row.copy(dirty = true)
        val outcome = run(source, store)
        assertEquals(1, outcome.uploaded)
        assertEquals("e-a'", source.events["a"]!!.etag)
        assertFalse(store.rows.values.single().dirty)
    }

    @Test
    fun `a deleted row deletes the event`() {
        val source = FakeSource()
        source.seed("a", "Stand-up")
        val store = FakeStore()
        run(source, store)
        val row = store.rows.values.single()
        store.rows[row.local] = row.copy(deleted = true)
        val outcome = run(source, store)
        assertEquals(1, outcome.removed)
        assertTrue(source.events.isEmpty())
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun `an event created and deleted on the phone between syncs just goes`() {
        val source = FakeSource()
        val store = FakeStore()
        run(source, store)
        val calendar = store.calendars().single()
        store.make(calendar.local, dirty = true, deleted = true)
        val outcome = run(source, store)
        assertEquals(1, outcome.removed)
        assertTrue(source.events.isEmpty())
        assertTrue(store.rows.isEmpty())
    }

    /**
     * The provider keeps no version on an event, so an edit made while the
     * upload is in flight can only be caught by reading the rows back. It must
     * leave the row dirty: cleared, the edit would never go up.
     */
    @Test
    fun `an edit made during the upload keeps the row dirty`() {
        val source = FakeSource()
        source.seed("a", "Stand-up")
        val store = FakeStore()
        run(source, store)
        val row = store.rows.values.single()
        store.rows[row.local] = row.copy(dirty = true)
        store.churn = true
        val outcome = run(source, store)
        assertEquals(1, outcome.uploaded)
        assertTrue("the edit still has to go up", store.rows.values.single().dirty)
        assertEquals("but the server's id and etag are stored", "e-a'", store.rows.values.single().etag)
    }

    // ---- 3. Conflict ----

    @Test
    fun `the server wins a conflict and the phone's edit is dropped`() {
        val source = FakeSource()
        source.seed("a", "Stand-up")
        val store = FakeStore()
        run(source, store)
        val row = store.rows.values.single()
        store.rows[row.local] = row.copy(dirty = true, etag = "stale")
        val outcome = run(source, store)
        assertEquals(0, outcome.uploaded)
        assertEquals(1, outcome.downloaded)
        assertEquals("e-a", store.rows.values.single().etag)
        assertFalse(store.rows.values.single().dirty)
    }

    @Test
    fun `a local delete against a changed event is not applied and the event comes back`() {
        val source = FakeSource()
        source.seed("a", "Stand-up")
        val store = FakeStore()
        run(source, store)
        val row = store.rows.values.single()
        store.rows[row.local] = row.copy(deleted = true, etag = "stale")
        val outcome = run(source, store)
        assertEquals(1, outcome.downloaded)
        assertTrue(source.events.containsKey("a"))
        assertFalse(store.rows.values.single().deleted)
    }

    @Test
    fun `an event that has gone from the server takes its rows with it`() {
        val source = FakeSource()
        source.seed("a", "Stand-up")
        val store = FakeStore()
        run(source, store)
        val row = store.rows.values.single()
        store.rows[row.local] = row.copy(dirty = true)
        source.vanish = true
        val outcome = run(source, store)
        assertEquals(1, outcome.removed)
        assertTrue(store.rows.isEmpty())
    }

    // ---- 4. Retry ----

    @Test
    fun `a transport failure propagates with the dirty flags still set`() {
        val source = FakeSource()
        source.seed("a", "Stand-up")
        val store = FakeStore()
        run(source, store)
        val row = store.rows.values.single()
        store.rows[row.local] = row.copy(dirty = true)
        source.offline = true
        try {
            run(source, store)
            fail("expected the transport failure to propagate")
        } catch (_: IOException) {
            // as it should
        }
        assertTrue(store.rows.values.single().dirty)
    }

    @Test
    fun `an authorization failure propagates for the adapter to report as hard`() {
        val source = FakeSource()
        val store = FakeStore()
        source.unauthorized = true
        try {
            run(source, store)
            fail("expected the authorization failure to propagate")
        } catch (_: SyncAuthorization) {
            // as it should
        }
    }

    @Test
    fun `an event the server refuses is reported and the rest of the run carries on`() {
        val source = FakeSource()
        val store = FakeStore()
        run(source, store)
        val calendar = store.calendars().single()
        store.make(calendar.local, dirty = true)
        source.refuse = "that event is not acceptable"
        val outcome = run(source, store)
        assertEquals(listOf("that event is not acceptable"), outcome.failures)
        assertTrue("the row stays for the next attempt", store.rows.values.single().dirty)
    }

    // ---- read-only calendars ----

    @Test
    fun `an edit in a read-only calendar is replaced by the server's copy`() {
        val source = FakeSource()
        source.calendars["calendar-2"] = SyncedCalendar("calendar-2", "Subscribed", kind = "subscription", readonly = true)
        source.seed("a", "Public holiday", calendar = "calendar-2")
        val store = FakeStore()
        run(source, store)
        val row = store.rows.values.single()
        store.rows[row.local] = row.copy(dirty = true)
        source.calls.clear()
        val outcome = run(source, store)
        assertTrue("no write went out", source.calls.none { it.startsWith("update") || it.startsWith("create") })
        assertEquals(1, outcome.downloaded)
        assertFalse(store.rows.values.single().dirty)
    }
}
