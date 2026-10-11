// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.calendars.api.CalendarsApi
import org.mochios.calendars.api.MenuApi
import org.mochios.calendars.navigation.CalendarsApp
import org.mochios.calendars.repository.CalendarsRepository
import org.mochios.calendars.storage.VisibilityStore
import org.mochios.calendars.ui.calendar.CalendarViewModel
import org.mochios.calendars.ui.editor.EventEditViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A calendar on its own, as a shortcut to it on the home screen opens it:
 * the other calendars are hidden for the visit, the calendars the device
 * shows are left as they were for Back to return to, and a new event lands in
 * the calendar when it can be written to.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CalendarSoleTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var repository: CalendarsRepository

    /** The calendars each events request asked for. */
    private val asked = ConcurrentLinkedQueue<Set<String>>()

    @Before
    fun begin() {
        AppContext.set(context)
        // The device's own choice: the holidays are hidden.
        VisibilityStore.hidden(context, setOf("c3"))
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringAfter("/calendars/").substringBefore("?")
                return when (path) {
                    "-/calendars" -> ok(
                        """{"calendars": [{"id": "c1", "name": "Home", "default": true},
                            {"id": "c2", "name": "Work"},
                            {"id": "c3", "name": "Holidays", "kind": "subscription", "readonly": true}]}""",
                    )
                    "-/preferences/get" -> ok("""{"preferences": {}}""")
                    "-/events/bounds" -> ok("""{"first": 0, "last": 0, "endless": false}""")
                    "-/events" -> {
                        asked.add(request.requestUrl?.queryParameter("calendars").orEmpty().split(",").toSet())
                        ok("""{"instances": [], "truncated": false}""")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/calendars/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        repository = CalendarsRepository(
            retrofit.create(CalendarsApi::class.java),
            retrofit.create(MenuApi::class.java),
            FileStore(context),
            context,
        )
    }

    @After
    fun end() = server.shutdown()

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun until(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    private fun model(): CalendarViewModel = CalendarViewModel(context, repository, London).also { model ->
        until { !model.uiState.value.isLoading && asked.isNotEmpty() }
    }

    @Test
    fun `a calendar on its own hides the others for the visit only`() {
        val model = model()
        model.sole("c2")
        until { asked.last() == setOf("c2") }
        assertEquals("c2", model.uiState.value.sole)
        assertEquals(setOf("c1", "c3"), model.uiState.value.hidden)
        assertEquals(setOf("c3"), VisibilityStore.hidden(context))
    }

    @Test
    fun `leaving it shows what the device shows again`() {
        val model = model()
        model.sole("c3")
        until { asked.last() == setOf("c3") }
        model.whole()
        until { asked.last() == setOf("c1", "c2") }
        assertNull(model.uiState.value.sole)
        assertEquals(setOf("c3"), model.uiState.value.hidden)
    }

    @Test
    fun `an event saved into another calendar widens the view, one saved into it does not`() {
        val model = model()
        model.sole("c2")
        until { asked.last() == setOf("c2") }
        VisibilityStore.reveal(context, "c2")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("c2", model.uiState.value.sole)
        VisibilityStore.reveal(context, "c1")
        until { model.uiState.value.sole == null }
        assertEquals(setOf("c3"), model.uiState.value.hidden)
    }

    @Test
    fun `a calendar gone from the list is not shown on its own`() {
        val model = model()
        model.sole("c9")
        model.reload()
        until { model.uiState.value.sole == null }
        assertEquals(setOf("c3"), model.uiState.value.hidden)
    }

    @Test
    fun `a reminder opens the whole calendar`() {
        val model = model()
        model.sole("c2")
        model.remind("e1", 0, null)
        assertNull(model.uiState.value.sole)
        assertEquals(setOf("c3"), model.uiState.value.hidden)
    }

    @Test
    fun `the device's own choices made meanwhile wait for the view to end`() {
        val model = model()
        model.sole("c2")
        until { asked.last() == setOf("c2") }
        model.toggle("c3")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(setOf("c1", "c3"), model.uiState.value.hidden)
        model.whole()
        assertEquals(emptySet<String>(), model.uiState.value.hidden)
    }

    private fun editor(vararg route: Pair<String, String>): EventEditViewModel =
        EventEditViewModel(context, repository, London, SavedStateHandle(mapOf(*route))).also { model ->
            until { model.uiState.value.calendar.isNotEmpty() }
        }

    @Test
    fun `a new event lands in the calendar viewed on its own`() {
        assertEquals("c2", editor("calendar" to "c2").uiState.value.calendar)
    }

    @Test
    fun `but not in one that cannot be written to, nor without one`() {
        assertEquals("c1", editor("calendar" to "c3").uiState.value.calendar)
        assertEquals("c1", editor().uiState.value.calendar)
    }

    @Test
    fun `a shortcut's link names its calendar, and no other link does`() {
        assertEquals("c2", CalendarsApp.sole("calendars/calendar/c2"))
        assertEquals("c2", CalendarsApp.sole("/calendars/calendar/c2"))
        // A reminder from before the day was written: an event and its start.
        assertNull(CalendarsApp.sole("calendars/e1/1700000000"))
        assertNull(CalendarsApp.sole("calendars/calendar/c2/settings"))
        assertNull(CalendarsApp.sole("calendars/calendar/"))
        assertNull(CalendarsApp.sole("calendars"))
    }

    @Test
    fun `a new event asked for from a calendar on its own carries it`() {
        assertEquals("calendars/events/new?start=5&calendar=c2", CalendarsApp.newEvent(5, calendar = "c2"))
        assertEquals("calendars/events/new?start=5", CalendarsApp.newEvent(5))
    }
}
