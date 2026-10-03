// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import android.net.Uri
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.files.FileStore
import org.mochios.calendars.api.CalendarsApi
import org.mochios.calendars.api.MenuApi
import org.mochios.calendars.repository.CalendarsRepository
import org.mochios.calendars.ui.calendar.Tally
import org.mochios.calendars.ui.calendar.rounds
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * An iCalendar import goes up once and is then written round by round: the
 * file on the first round, and only the staged id and the offset reached on
 * each after it, until the server says it has finished. An export is the
 * calendar's own text, written byte for byte where the user chose.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TransferTest {

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

    /** The value of the multipart part [name] in [body], or null when it is not there. */
    private fun part(body: String, name: String): String? =
        Regex("""name="$name"(?:; filename="[^"]*")?\r\n(?:[^\r\n]+\r\n)*\r\n([^\r\n]*)""").find(body)?.groupValues?.get(1)

    private fun round(offset: Int, imported: Int, skipped: Int, failed: Int, finished: Boolean) = MockResponse().setBody(
        """{"data": {"import": "staged1", "offset": $offset, "total": 450, "imported": $imported, """ +
            """"skipped": $skipped, "failed": $failed, "finished": $finished}}""",
    )

    @Test
    fun `the file goes up once, then the staged id and offset until the import finishes`() = runBlocking {
        server.enqueue(round(200, 190, 7, 3, false))
        server.enqueue(round(400, 200, 0, 0, false))
        server.enqueue(round(450, 40, 5, 5, true))
        val file = File.createTempFile("work", ".ics").apply {
            deleteOnExit()
            writeText("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nEND:VCALENDAR\r\n")
        }
        val seen = mutableListOf<Int>()

        val tally = rounds(
            file,
            Tally(calendar = "calendar1", name = "Work"),
            round = { part, staged, offset -> repository.importRound("calendar1", part, staged, offset) },
            progress = { seen += it.done },
        )

        assertEquals(Tally("calendar1", "Work", 450, 450, 430, 12, 8, true), tally)
        assertEquals(listOf(200, 400, 450), seen)
        val requests = (1..3).map { server.takeRequest() }
        requests.forEach { assertEquals("/calendars/-/calendars/import", it.path) }
        val bodies = requests.map { it.body.readUtf8() }

        assertEquals("calendar1", part(bodies[0], "calendar"))
        assertEquals("0", part(bodies[0], "offset"))
        assertNull(part(bodies[0], "import"))
        assertTrue(bodies[0], bodies[0].contains("""name="file"; filename="${file.name}""""))
        assertEquals("BEGIN:VCALENDAR", part(bodies[0], "file"))

        for ((body, offset) in listOf(bodies[1] to "200", bodies[2] to "400")) {
            assertEquals("calendar1", part(body, "calendar"))
            assertEquals("staged1", part(body, "import"))
            assertEquals(offset, part(body, "offset"))
            assertNull(body, part(body, "file"))
        }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `an export writes the calendar's bytes where the user chose`() = runBlocking {
        val served = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nX-WR-CALNAME:Café ☕\r\nEND:VCALENDAR\r\n"
        server.enqueue(MockResponse().setHeader("Content-Type", "text/calendar; charset=utf-8").setBody(served))
        val destination = Uri.parse("content://org.mochios.test/Work.ics")
        val written = ByteArrayOutputStream()
        shadowOf(RuntimeEnvironment.getApplication().contentResolver).registerOutputStream(destination, written)

        val text = repository.exportCalendar("calendar1")
        assertTrue(repository.saveTextFile(destination, text))

        assertEquals("/calendars/-/calendars/export?calendar=calendar1", server.takeRequest().path)
        assertArrayEquals(served.toByteArray(Charsets.UTF_8), written.toByteArray())
    }
}
