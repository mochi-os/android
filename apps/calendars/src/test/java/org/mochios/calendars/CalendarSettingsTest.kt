// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.R as MochiR
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.calendars.api.CalendarsApi
import org.mochios.calendars.api.MenuApi
import org.mochios.calendars.repository.CalendarsRepository
import org.mochios.calendars.ui.settings.CalendarSettingsScreen
import org.mochios.calendars.ui.settings.CalendarSettingsViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A calendar's settings screen: Save stays off until the name or colour
 * changes, sends only what changed, and the address can be revoked from it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CalendarSettingsTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private val asked = ConcurrentLinkedQueue<RecordedRequest>()
    private var saved: Boolean? = null

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                asked.add(request)
                val path = request.path.orEmpty().substringAfter("/calendars/").substringBefore("?")
                return when (path) {
                    "-/calendars" -> ok(
                        """{"calendars": [{"id": "c1", "name": "Home", "colour": "#60a5fa",
                            "default": true}]}""",
                    )
                    "-/calendars/rename", "-/calendars/colour" -> ok(
                        """{"calendar": {"id": "c1", "name": "Family", "colour": "#60a5fa"}}""",
                    )
                    "-/link/revoke" -> ok("""{"revoked": true}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun end() = server.shutdown()

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun paths() =
        asked.map { request -> request.path.orEmpty().substringAfter("/calendars/") }

    private fun show(calendar: String = "c1"): CalendarSettingsViewModel {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/calendars/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val repository = CalendarsRepository(
            retrofit.create(CalendarsApi::class.java),
            retrofit.create(MenuApi::class.java),
            FileStore(context),
        )
        val handle = SavedStateHandle(mapOf("calendar" to calendar))
        val model = CalendarSettingsViewModel(repository, London, handle)
        rule.setContent {
            CalendarSettingsScreen(
                onBack = {},
                onSaved = { renamed -> saved = renamed },
                viewModel = model,
            )
        }
        return model
    }

    private fun waitFor(text: String) =
        rule.waitUntil(5_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun answered(done: () -> Boolean) = rule.waitUntil(5_000) {
        rule.onAllNodes(isRoot()).fetchSemanticsNodes()
        done()
    }

    private fun save() = rule.onNodeWithText(context.getString(MochiR.string.common_save))

    @Test
    fun `Save is off until the name changes, then renames and nothing else`() {
        show()
        waitFor("Home")
        save().assertIsNotEnabled()
        val name = rule.onAllNodes(hasSetTextAction())[0]
        name.performTextReplacement("Family")
        save().assertIsEnabled()
        name.performTextReplacement("Home")
        save().assertIsNotEnabled()
        name.performTextReplacement("Family")
        save().assertIsEnabled().performClick()
        answered { saved != null }
        assertEquals(true, saved)
        assertTrue(paths().any { path -> path.startsWith("-/calendars/rename") })
        assertTrue(paths().none { path -> path.startsWith("-/calendars/colour") })
    }

    @Test
    fun `a new colour alone is saved without a rename`() {
        val model = show()
        waitFor("Home")
        rule.runOnIdle { model.colour("#f87171") }
        save().assertIsEnabled().performClick()
        answered { saved != null }
        assertEquals(false, saved)
        assertTrue(paths().any { path -> path.startsWith("-/calendars/colour") })
        assertTrue(paths().none { path -> path.startsWith("-/calendars/rename") })
    }

    @Test
    fun `the address is revoked from the top bar's menu once confirmed`() {
        show()
        waitFor("Home")
        val revoke = context.getString(R.string.calendars_link_revoke)
        rule.onNodeWithText(revoke).assertDoesNotExist()
        rule.onNodeWithContentDescription(context.getString(MochiR.string.common_more_options))
            .performClick()
        rule.onNodeWithText(context.getString(R.string.calendars_link_copy)).assertExists()
        rule.onNodeWithText(revoke).performClick()
        waitFor(context.getString(R.string.calendars_link_revoke_title))
        rule.onAllNodesWithText(revoke)[0].performClick()
        waitFor(context.getString(R.string.calendars_link_revoked))
        assertEquals(1, paths().count { path -> path.startsWith("-/link/revoke") })
    }

    @Test
    fun `a calendar that is no longer there shows Retry`() {
        show("gone")
        waitFor(context.getString(MochiR.string.common_retry))
    }
}
