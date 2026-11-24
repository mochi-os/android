// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.moderation

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.compose.rememberNavController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.staff.StaffServer
import org.mochios.staff.StaffServer.Companion.ok
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The moderation log is searched by listing title, as typed, letters and all;
 * opened from a listing's history it holds to that listing until cleared.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModerationLogTest {

    @get:Rule
    val rule = createComposeRule()

    private val server = StaffServer { request ->
        if (request.requestUrl!!.encodedPath.endsWith("-/moderation/log")) {
            ok("""{"log": [{"id": "m1", "listing": "l1", "listing_title": "Teapot", "action": "hold"}], "total": 1, "next": ""}""")
        } else {
            null
        }
    }

    @Before
    fun begin() {
        server.start()
    }

    @After
    fun end() = server.stop()

    private fun log() = server.made("-/moderation/log")

    private fun model(handle: SavedStateHandle = SavedStateHandle()): ModerationLogViewModel {
        val model = ModerationLogViewModel(server.repository, server.socket, server.session, handle)
        server.until { !model.state.value.isLoading }
        return model
    }

    @Test
    fun `opened for a listing, the log asks for that listing until cleared`() {
        val model = model(SavedStateHandle(mapOf(ModerationLogViewModel.KEY_LISTING to "l1")))
        assertEquals("l1", log().last().requestUrl!!.queryParameter("listing"))
        model.clearListing()
        server.until { log().size == 2 && !model.state.value.isLoading }
        assertNull(log().last().requestUrl!!.queryParameter("listing"))
    }

    @Test
    fun `a title typed in the search is sent as the query, as typed`() {
        val model = model()
        rule.setContent { ModerationLogScreen(navController = rememberNavController(), viewModel = model) }
        rule.onNode(hasSetTextAction()).performTextInput("Tea pot 2")
        rule.mainClock.advanceTimeBy(400)
        server.until { log().any { request -> request.requestUrl!!.queryParameter("query") == "Tea pot 2" } }
        rule.onNodeWithText("Tea pot 2").assertExists()
        assertNull(log().last().requestUrl!!.queryParameter("listing"))
    }
}
