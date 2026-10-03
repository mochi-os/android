// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import android.net.Uri
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.calendars.api.CalendarsApi
import org.mochios.calendars.api.MenuApi
import org.mochios.calendars.model.Calendar
import org.mochios.calendars.repository.CalendarsRepository
import org.mochios.calendars.storage.VisibilityStore
import org.mochios.calendars.ui.calendar.CalendarViewModel
import org.mochios.calendars.ui.calendar.Tally
import org.mochios.calendars.ui.dialogs.ImportDialog
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.mochios.android.R as MochiR

/**
 * A failed import as the web's: the dialog stays open on what went wrong
 * and offers to try the same file again, which imports it, and Cancel lets
 * go of the copy kept for the retry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImportRetryTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer

    /** How many more import rounds fail, and how many were asked for. */
    private val failures = AtomicInteger(0)
    private val rounds = AtomicInteger(0)

    private val home = Calendar(id = "c1", name = "Home", default = true)

    @Before
    fun begin() {
        AppContext.set(context)
        VisibilityStore.hidden(context, emptySet())
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringAfter("/calendars/").substringBefore("?")
                return when (path) {
                    "-/calendars" -> ok("""{"calendars": [{"id": "c1", "name": "Home", "default": true}]}""")
                    "-/preferences/get", "-/preferences/set" -> ok("""{"preferences": {}}""")
                    "-/events/bounds" -> ok("""{"first": 0, "last": 0, "endless": false}""")
                    "-/events" -> ok("""{"instances": [], "truncated": false}""")
                    "-/calendars/import" -> {
                        rounds.incrementAndGet()
                        if (failures.getAndDecrement() > 0) {
                            MockResponse().setResponseCode(500).setBody("""{"error": "The server is busy"}""")
                        } else {
                            ok("""{"import": "s1", "offset": 2, "total": 2, "imported": 2, "finished": true}""")
                        }
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun end() = server.shutdown()

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun model(): CalendarViewModel {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/calendars/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val repository = CalendarsRepository(
            retrofit.create(CalendarsApi::class.java),
            retrofit.create(MenuApi::class.java),
            FileStore(context),
        )
        return CalendarViewModel(context, repository, London).also { model ->
            until { !model.uiState.value.isLoading }
        }
    }

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    /** A picked file, outside the cache the import stages its copy in. */
    private fun picked(): Uri {
        val file = File(context.filesDir, "holidays.ics")
        file.writeText("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nEND:VCALENDAR\r\n")
        return Uri.fromFile(file)
    }

    /** The staged copies in the cache. */
    private fun staged() = context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("holidays") }

    @Test
    fun `a failed import stays open on the reason, and Retry imports the same file`() {
        failures.set(1)
        val model = model()
        model.import(home, picked())
        until { model.importing.value?.error != null }
        assertNotNull(model.importing.value)
        assertEquals(1, staged().size)
        model.retryImport()
        until { model.importing.value?.finished == true }
        assertEquals(2, rounds.get())
        assertNull(model.importing.value?.error)
        assertEquals(2, model.importing.value?.imported)
        // The copy goes once the retry has finished with it.
        until { staged().isEmpty() }
        model.closeImport()
        assertNull(model.importing.value)
    }

    @Test
    fun `closing a failed import lets go of its copy`() {
        failures.set(1)
        val model = model()
        model.import(home, picked())
        until { model.importing.value?.error != null }
        model.closeImport()
        assertNull(model.importing.value)
        until { staged().isEmpty() }
        // Nothing is left to retry.
        model.retryImport()
        until { true }
        assertNull(model.importing.value)
        assertEquals(1, rounds.get())
    }

    @Test
    fun `the failed dialog says why and offers Retry and Cancel`() {
        val retried = mutableListOf<Unit>()
        val closed = mutableListOf<Unit>()
        val failed = Tally(calendar = "c1", name = "Home", error = MochiError.Unknown("The server is busy"))
        rule.setContent { ImportDialog(tally = failed, onClose = { closed += Unit }, onRetry = { retried += Unit }) }
        rule.onNodeWithText("The server is busy").assertExists()
        rule.onNodeWithText(context.getString(MochiR.string.common_retry)).performClick()
        rule.onNodeWithText(context.getString(MochiR.string.common_cancel)).performClick()
        rule.waitForIdle()
        assertEquals(1, retried.size)
        assertEquals(1, closed.size)
        assertTrue(rule.onAllNodesWithText(context.getString(MochiR.string.common_close)).fetchSemanticsNodes().isEmpty())
    }
}
