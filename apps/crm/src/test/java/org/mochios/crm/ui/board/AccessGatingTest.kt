// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.SavedStateHandle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.ui.components.dnd.DragEdge
import org.mochios.android.ui.components.dnd.DragState
import org.mochios.android.ui.components.dnd.DropOrientation
import org.mochios.android.ui.components.dnd.dropTarget
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.crm.CrmServer
import org.mochios.crm.R
import org.mochios.crm.lib.ActiveViewStore
import org.mochios.crm.lib.CollapsedColumnsStore
import org.mochios.crm.ui.crm.CrmViewModel
import org.mochios.crm.ui.tree.TreeView
import org.mochios.crm.until
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.mochios.android.R as MochiR

/**
 * The board and the tree offer what the user's access lets them do, as the
 * web does: the design's columns to those who may change the design, moving,
 * creating and deleting objects to those who may write, and to a viewer
 * neither. A card the writer drops another on takes it wherever it lands.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class AccessGatingTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: CrmServer
    private lateinit var socket: MochiWebSocket

    @Before
    fun begin() {
        server = CrmServer(context)
        server.data("c1/-/people", """{"people": []}""")
        server.data("c1/-/objects", """{"objects": [{"id": "a", "class": "company", "parent": "", "rank": "a0", "values": {"name": "Acme", "stage": "lead"}}], "watched": []}""")
        socket = server.socket()
    }

    @After
    fun end() {
        socket.disconnectAll()
        server.stop()
    }

    private fun model(access: String, viewtype: String = "board"): CrmViewModel {
        server.data("c1/-/information", """{"crm": {"id": "c1", "name": "Sales", "access": "$access"},
            "classes": [{"id": "company", "name": "Company", "title": "name"}],
            "fields": {"company": [{"id": "name", "name": "Name", "fieldtype": "text"}, {"id": "stage", "name": "Stage", "fieldtype": "enumerated"}]},
            "options": {"company": {"stage": [{"id": "lead", "name": "Lead"}]}},
            "views": [{"id": "v1", "name": "View", "viewtype": "$viewtype", "columns": "stage", "classes": ["company"]}],
            "hierarchy": {"company": [""]}}""")
        val model = CrmViewModel(
            SavedStateHandle(mapOf("crmId" to "c1")),
            server.repository(),
            socket,
            server.session,
            ActiveViewStore(context),
            CollapsedColumnsStore(context),
        )
        until("the CRM") { model.uiState.value.crmDetails != null && model.uiState.value.objects.isNotEmpty() }
        return model
    }

    private fun board(access: String): CrmViewModel {
        val model = model(access)
        rule.setContent {
            val state by model.uiState.collectAsState()
            BoardView(
                objects = state.objects,
                view = model.getActiveView(),
                viewModel = model,
                crmDetails = state.crmDetails,
                people = emptyList(),
                onObjectClick = {},
                onStartCreate = {},
            )
        }
        rule.waitForIdle()
        return model
    }

    private fun tree(access: String) {
        val model = model(access, "list")
        rule.setContent {
            val state by model.uiState.collectAsState()
            TreeView(
                objects = state.objects,
                view = model.getActiveView(),
                viewModel = model,
                crmDetails = state.crmDetails,
                people = emptyList(),
                allObjects = state.objects,
                onObjectClick = {},
            )
        }
        rule.waitForIdle()
    }

    private fun described(id: Int) = rule.onAllNodesWithContentDescription(
        context.getString(id), useUnmergedTree = true,
    ).fetchSemanticsNodes().size

    private fun offered(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `the access levels allow what the web's do`() {
        mapOf(
            "owner" to listOf(true, true, true),
            "design" to listOf(true, true, true),
            "write" to listOf(false, true, true),
            "comment" to listOf(false, false, false),
            "view" to listOf(false, false, false),
        ).forEach { (access, allowed) ->
            val model = model(access)
            assertEquals(access, allowed, listOf(model.canDesign(), model.canWrite(), model.canCreate()))
        }
    }

    @Test
    fun `a viewer's board offers no column change, no create, no move and no drag`() {
        board("view")
        assertEquals("no column \"+\"", 0, described(R.string.crm_board_new))
        assertEquals("no card drag", 0, described(R.string.crm_drag_card))
        assertEquals("no column reorder", 0, described(R.string.crm_drag_column))
        // The column's menu holds Collapse alone; the card has no menu at all.
        assertEquals(1, described(MochiR.string.common_more_options))
        rule.onNodeWithContentDescription(context.getString(MochiR.string.common_more_options)).performClick()
        rule.waitForIdle()
        assertTrue(offered(context.getString(MochiR.string.common_collapse)))
        assertFalse(offered(context.getString(R.string.crm_board_rename)))
    }

    @Test
    fun `a writer's board moves and creates objects but leaves the columns alone`() {
        board("write")
        assertEquals(1, described(R.string.crm_board_new))
        assertEquals(1, described(R.string.crm_drag_card))
        assertEquals(0, described(R.string.crm_drag_column))
        // The column's menu and the card's.
        assertEquals(2, described(MochiR.string.common_more_options))
    }

    @Test
    fun `a designer's board reorders and renames columns`() {
        board("design")
        assertEquals(1, described(R.string.crm_drag_column))
        rule.onAllNodesWithContentDescription(context.getString(MochiR.string.common_more_options))[0].performClick()
        rule.waitForIdle()
        assertTrue(offered(context.getString(R.string.crm_board_rename)))
    }

    @Test
    fun `a viewer's tree rows neither drag nor offer Move or Delete`() {
        tree("view")
        assertEquals(0, described(R.string.crm_drag_row))
        assertEquals(0, described(MochiR.string.common_more_options))
    }

    @Test
    fun `a writer's tree rows drag and offer Delete`() {
        tree("write")
        assertEquals(1, described(R.string.crm_drag_row))
        rule.onNodeWithContentDescription(context.getString(MochiR.string.common_more_options)).performClick()
        rule.waitForIdle()
        assertTrue(offered(context.getString(MochiR.string.common_delete)))
    }

    @Test
    fun `a card takes a drop anywhere on it, its middle too`() {
        lateinit var state: DragState
        var dropped: Pair<String, DragEdge>? = null
        rule.setContent {
            state = rememberCardDragState()
            Box(
                Modifier
                    .size(200.dp)
                    .dropTarget(state, "column", DropOrientation.OnOnly) { _, edge -> dropped = "column" to edge }
            ) {
                Box(
                    Modifier
                        .size(200.dp, 60.dp)
                        .testTag("card")
                        .dropTarget(state, "card", DropOrientation.Vertical, setOf(DragEdge.Top, DragEdge.Bottom)) { _, edge ->
                            dropped = "card" to edge
                        }
                )
            }
        }
        rule.waitForIdle()
        val card = rule.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot
        for ((fraction, edge) in listOf(0.4f to DragEdge.Top, 0.6f to DragEdge.Bottom)) {
            dropped = null
            state.startDrag("source", Offset(card.center.x, card.top + card.height * fraction))
            state.endDrag()
            assertEquals("THE POINT: the card's middle is its own, not the column's", "card" to edge, dropped)
        }
    }

    @Test
    fun `a card dropped on another in its own column names no column`() {
        server.data("c1/-/objects", """{"objects": [
            {"id": "a", "class": "company", "parent": "", "rank": "a0", "values": {"name": "Acme", "stage": "lead"}},
            {"id": "b", "class": "company", "parent": "", "rank": "a1", "values": {"name": "Bolt", "stage": "lead"}}], "watched": []}""")
        board("write")
        until("both cards") { rule.onAllNodesWithText("Bolt").fetchSemanticsNodes().isNotEmpty() }
        val bolt = rule.onNodeWithText("Bolt").fetchSemanticsNode().boundsInRoot
        rule.onRoot().performTouchInput {
            down(bolt.center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(Offset(0f, 4f))
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        val acme = rule.onNodeWithText("Acme").fetchSemanticsNode().boundsInRoot
        rule.onRoot().performTouchInput {
            moveTo(Offset(acme.center.x, acme.top + 1f))
            up()
        }
        until("the move") { server.of("c1/-/objects/b/move").isNotEmpty() }
        val move = server.of("c1/-/objects/b/move").single().json()
        assertFalse("THE POINT: the column does not change, so it is not named", move.has("field") || move.has("value"))
        assertEquals("above Acme", "a", move.get("before").asString)
    }
}
