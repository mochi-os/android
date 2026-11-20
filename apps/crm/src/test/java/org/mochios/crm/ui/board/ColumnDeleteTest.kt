// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.board

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.crm.CrmServer
import org.mochios.crm.R
import org.mochios.crm.lib.ActiveViewStore
import org.mochios.crm.lib.CollapsedColumnsStore
import org.mochios.crm.settle
import org.mochios.crm.ui.crm.CrmViewModel
import org.mochios.crm.until
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.mochios.android.R as MochiR

/**
 * A board column's Delete, as the web offers it: only on a column nothing
 * holds, counted over every object of the CRM, only to those who may change
 * the design, and only once the user has confirmed it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class ColumnDeleteTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: CrmServer
    private lateinit var socket: MochiWebSocket

    private val delete = "c1/-/classes/company/fields/stage/options/lost/delete"

    @Before
    fun begin() {
        server = CrmServer(context)
        server.data("c1/-/people", """{"people": []}""")
        socket = server.socket()
    }

    @After
    fun end() {
        socket.disconnectAll()
        server.stop()
    }

    /** The board of a CRM the user holds [access] on, with [objects]. */
    private fun show(access: String, objects: String): CrmViewModel {
        server.data("c1/-/information", """{"crm": {"id": "c1", "name": "Sales", "access": "$access"},
            "classes": [{"id": "company", "name": "Company", "title": "name"}, {"id": "contact", "name": "Contact", "title": "name"}],
            "fields": {"company": [{"id": "stage", "name": "Stage", "fieldtype": "enumerated"}]},
            "options": {"company": {"stage": [{"id": "lost", "name": "Lost"}]}},
            "views": [{"id": "v1", "name": "Board", "viewtype": "board", "columns": "stage", "classes": ["company"]}],
            "hierarchy": {"company": [""], "contact": ["", "company"]}}""")
        server.data("c1/-/objects", """{"objects": $objects, "watched": []}""")
        val model = CrmViewModel(
            SavedStateHandle(mapOf("crmId" to "c1")),
            server.repository(),
            socket,
            server.session,
            ActiveViewStore(context),
            CollapsedColumnsStore(context),
        )
        until("the board") { model.uiState.value.crmDetails != null && !model.uiState.value.isLoading }
        rule.setContent {
            val state by model.uiState.collectAsState()
            BoardView(
                objects = state.objects,
                view = model.getActiveView(),
                viewModel = model,
                crmDetails = state.crmDetails,
                people = emptyList(),
                onObjectClick = {},
            )
        }
        rule.waitForIdle()
        rule.onNodeWithContentDescription(context.getString(MochiR.string.common_more_options)).performClick()
        rule.waitForIdle()
        return model
    }

    private fun offered(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `an empty column is deleted once confirmed`() {
        show("design", "[]")
        rule.onNodeWithText(context.getString(MochiR.string.common_delete)).performClick()
        rule.waitForIdle()
        assertTrue(offered(context.getString(R.string.crm_board_delete_column)))
        assertTrue(offered("Are you sure you want to delete the \"Lost\" column? This cannot be undone."))
        assertEquals("nothing is deleted before the confirmation", emptyList<Any>(), server.of(delete))
        rule.onNodeWithText(context.getString(MochiR.string.common_delete)).performClick()
        until("the delete") { server.of(delete).isNotEmpty() }
    }

    @Test
    fun `a column that only a hidden object holds offers no Delete`() {
        // A contact, which this view leaves out, still holds the option.
        show("owner", """[{"id": "k1", "class": "contact", "parent": "", "rank": "a0", "values": {"stage": "lost"}}]""")
        assertTrue(offered(context.getString(R.string.crm_board_rename)))
        assertTrue(!offered(context.getString(MochiR.string.common_delete)))
    }

    @Test
    fun `without design access a column offers no Delete`() {
        show("write", "[]")
        assertTrue(offered(context.getString(R.string.crm_board_rename)))
        assertTrue(!offered(context.getString(MochiR.string.common_delete)))
        settle(200)
        assertEquals(emptyList<Any>(), server.of(delete))
    }
}
