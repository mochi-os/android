// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.files.FileStore
import org.mochios.calendars.api.CalendarsApi
import org.mochios.calendars.api.MenuApi
import org.mochios.calendars.repository.CalendarsRepository
import org.mochios.calendars.repository.EventChangedException
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * What the editor's Undo needs from a delete: the series as a deleted
 * occurrence left it, to write the original back over, or nothing when the
 * whole event went; and the way back held for the calendar to take once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UndoTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: CalendarsRepository

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/calendars/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        repository = CalendarsRepository(
            retrofit.create(CalendarsApi::class.java),
            retrofit.create(MenuApi::class.java),
            FileStore(RuntimeEnvironment.getApplication()),
        )
    }

    @After
    fun stop() = server.shutdown()

    /** A weekly series from 2026-09-22 10:00 UTC, as the server answers it. */
    private fun series(etag: String) = MockResponse().setBody(
        """{"data": {"event": {"id": "e1", "calendar": "c1", "etag": "$etag", "recurring": true, "components": [
            {"name": "VEVENT", "properties": [
                {"name": "UID", "params": {}, "value": "uid-1"},
                {"name": "SUMMARY", "params": {}, "value": "Stand-up"},
                {"name": "DTSTART", "params": {}, "value": "20260922T100000Z"},
                {"name": "DTEND", "params": {}, "value": "20260922T110000Z"},
                {"name": "RRULE", "params": {}, "value": "FREQ=WEEKLY"}
            ], "components": []}
        ]}}}""",
    )

    @Test
    fun `deleting one occurrence answers the series it left, for Undo to write back over`() = runBlocking {
        server.enqueue(series("a"))
        server.enqueue(series("b"))
        val changed = repository.excludeOccurrence("e1", 1_790_676_000)
        assertEquals("e1", changed.id)
        assertEquals("b", changed.etag)
    }

    @Test
    fun `deleting from the first occurrence on deletes the event, so there is no series to answer`() = runBlocking {
        server.enqueue(series("a"))
        server.enqueue(MockResponse().setBody("""{"data": {}}"""))
        assertNull(repository.truncateEvent("e1", 1_790_071_200 - 3_600))
    }

    @Test
    fun `deleting the later occurrences answers the series cut short`() = runBlocking {
        server.enqueue(series("a"))
        server.enqueue(series("c"))
        val changed = repository.truncateEvent("e1", 1_790_676_000)
        assertNotNull(changed)
        assertEquals("c", changed!!.etag)
    }

    @Test
    fun `a delete over the event as the editor read it is refused when it changed since`() = runBlocking {
        // The stored copy is all the editor sends: no read first, and a 412 is
        // the editor's to answer with Reload rather than a write over the change.
        server.enqueue(series("a"))
        val stored = repository.getEvent("e1")
        server.enqueue(MockResponse().setResponseCode(412).setBody("""{"error": "changed"}"""))
        val one = runCatching { repository.excludeOccurrence("e1", 1_790_676_000, stored) }
        assertTrue(one.exceptionOrNull() is EventChangedException)
        server.enqueue(MockResponse().setResponseCode(412).setBody("""{"error": "changed"}"""))
        val following = runCatching { repository.truncateEvent("e1", 1_790_676_000, stored) }
        assertTrue(following.exceptionOrNull() is EventChangedException)
        // One read, then the two refused writes: nothing read again behind the editor's back.
        assertEquals(3, server.requestCount)
        server.takeRequest()
        val written = server.takeRequest().body.readUtf8()
        assertTrue(written, written.contains("\"etag\":\"a\""))
    }

    @Test
    fun `the way back from a delete is taken once`() {
        var restored = 0
        repository.deleted { restored++ }
        val back = repository.restoring()
        assertNotNull(back)
        runBlocking { back!!() }
        assertEquals(1, restored)
        assertNull(repository.restoring())
    }
}
