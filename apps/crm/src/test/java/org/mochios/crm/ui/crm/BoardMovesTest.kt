// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.crm

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.android.ui.components.dnd.DragEdge
import org.mochios.crm.CrmServer
import org.mochios.crm.lib.ActiveViewStore
import org.mochios.crm.lib.CollapsedColumnsStore
import org.mochios.crm.settle
import org.mochios.crm.ui.tree.TreeNode
import org.mochios.crm.ui.tree.handleTreeDrop
import org.mochios.crm.until
import org.mochios.android.websocket.MochiWebSocket
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The board's and the tree's moves as the server reads them: positions
 * counted in the list the server ranks, a top-level parent or scope that
 * arrives as "", a column delete only while nothing holds it, and the
 * failures that come after the CRM has loaded said where the user is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BoardMovesTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: CrmServer
    private lateinit var socket: MochiWebSocket
    private val failures = mutableListOf<MochiError>()
    private val jobs = mutableListOf<Job>()

    // Column "lead" in the server's rank order: a (company), c1 (a contact
    // under a, which the view hides), b (company), d (company). The board
    // shows a, b, d; the server counts c1 too.
    private val objects = """[
        {"id": "a", "class": "company", "parent": "", "rank": "a0", "values": {"stage": "lead", "status": "open"}},
        {"id": "c1", "class": "contact", "parent": "a", "rank": "a1", "values": {"stage": "lead", "status": "open"}},
        {"id": "b", "class": "company", "parent": "", "rank": "a2", "values": {"stage": "lead", "status": "closed"}},
        {"id": "d", "class": "company", "parent": "", "rank": "a3", "values": {"stage": "lead"}},
        {"id": "w", "class": "company", "parent": "", "rank": "a4", "values": {"stage": "won"}}
    ]"""

    @Before
    fun begin() {
        server = CrmServer(context)
        server.data("c1/-/information", """{"crm": {"id": "c1", "name": "Sales", "access": "owner"},
            "classes": [{"id": "company", "name": "Company", "title": "name"}, {"id": "contact", "name": "Contact", "title": "name"}],
            "fields": {"company": [{"id": "stage", "name": "Stage", "fieldtype": "enumerated"}, {"id": "status", "name": "Status", "fieldtype": "text"}]},
            "options": {"company": {"stage": [{"id": "lead", "name": "Lead"}, {"id": "won", "name": "Won"}, {"id": "lost", "name": "Lost"}]}},
            "views": [{"id": "v1", "name": "Board", "viewtype": "board", "columns": "stage", "filter": "status:open", "classes": ["company"]}],
            "hierarchy": {"company": [""], "contact": ["", "company"]}}""")
        server.data("c1/-/objects", """{"objects": $objects, "watched": []}""")
        server.data("c1/-/people", """{"people": []}""")
        socket = server.socket()
    }

    @After
    fun end() {
        jobs.forEach { job -> job.cancel() }
        socket.disconnectAll()
        server.stop()
    }

    private fun board(): CrmViewModel {
        val model = CrmViewModel(
            SavedStateHandle(mapOf("crmId" to "c1")),
            server.repository(),
            socket,
            server.session,
            ActiveViewStore(context),
            CollapsedColumnsStore(context),
        )
        jobs += CoroutineScope(Dispatchers.Main).launch { model.actionFailed.collect { failures += it } }
        until("the board") { model.uiState.value.objects.size == 5 && model.uiState.value.crmDetails != null }
        return model
    }

    @Test
    fun `a card dropped below another counts the objects the board does not show`() {
        val model = board()
        // Below b: the server's list without d is a, c1, b, so after b is 4.
        // The board's own list (a, b) would have said 3, landing above b.
        assertEquals(4, model.dropPosition("stage", "lead", targetId = "b", sourceId = "d", below = true))
        assertEquals(3, model.dropPosition("stage", "lead", targetId = "b", sourceId = "d", below = false))
        // From another column: w is not in lead's list, so nothing is taken out.
        assertEquals(2, model.dropPosition("stage", "lead", targetId = "c1", sourceId = "w", below = false))
    }

    @Test
    fun `a drop with no column value is counted in the column the object holds`() {
        val model = board()
        // An empty value leaves the stage as it is, so the server counts in w's own column.
        assertEquals(1, model.dropPosition("stage", "", targetId = "x", sourceId = "w", below = false))
        assertEquals(4, model.dropPosition("stage", "", targetId = "b", sourceId = "d", below = true))
    }

    @Test
    fun `a drop at the end of a column sends a position past every object`() {
        val model = board()
        model.moveToEnd("a", "stage", "won", rowField = "status", rowValue = "closed")
        until("the move") { server.of("c1/-/objects/a/move").isNotEmpty() }
        val move = server.of("c1/-/objects/a/move").single().json()
        assertEquals(Int.MAX_VALUE, move.get("rank").asInt)
        assertEquals("won", move.get("value").asString)
        assertEquals("""{"field":"status","value":"closed"}""", move.get("row").asString)
    }

    @Test
    fun `a move to the top level sends an empty parent the server reads`() {
        val model = board()
        model.reparentObject("c1", "")
        until("the update") { server.of("c1/-/objects/c1/update").isNotEmpty() }
        val update = server.of("c1/-/objects/c1/update").single()
        assertTrue(update.type, update.type.startsWith("application/json"))
        assertEquals("", update.json().get("parent").asString)
    }

    @Test
    fun `top-level rows reorder in a scope of the top level`() {
        val model = board()
        val target = TreeNode(model.uiState.value.objects.first { it.id == "b" }, depth = 0, hasChildren = false, isExpanded = true)
        handleTreeDrop(sourceId = "w", targetNode = target, edge = DragEdge.Bottom, viewModel = model)
        until("the move") { server.of("c1/-/objects/w/move").isNotEmpty() }
        val move = server.of("c1/-/objects/w/move").single().json()
        assertEquals("", move.get("scope").asString)
        // Top-level siblings without w: a, b, d; below b is 3.
        assertEquals(3, move.get("rank").asInt)
    }

    @Test
    fun `a column is only deleted while nothing holds it, hidden objects included`() {
        val model = board()
        assertTrue(model.canDesign())
        assertEquals(0, model.columnHolds("stage", "lost"))
        // c1 is hidden from the board, but it holds lead.
        assertEquals(4, model.columnHolds("stage", "lead"))
        model.deleteColumnOption("stage", "lead")
        settle()
        assertEquals(emptyList<Any>(), server.of("c1/-/classes/company/fields/stage/options/lead/delete"))
        model.deleteColumnOption("stage", "lost")
        until("the delete") { server.of("c1/-/classes/company/fields/stage/options/lost/delete").isNotEmpty() }
    }

    @Test
    fun `a view's stored filter is not applied to the list or the CSV`() {
        val model = board()
        val shown = model.getFilteredObjects().map { obj -> obj.id }.toSet()
        // The view keeps companies; its "status:open" filter would have left a alone.
        assertEquals(setOf("a", "b", "d", "w"), shown)
        model.exportCsv(listOf("ID", "Class", "Parent"))
        val csv = model.uiState.value.pendingExport?.content.orEmpty()
        assertTrue(csv, csv.lines().any { line -> line.startsWith("b,") })
        assertTrue(csv, csv.lines().any { line -> line.startsWith("d,") })
    }

    @Test
    fun `a refresh that fails over a loaded CRM is said, not hidden in the page error`() {
        val model = board()
        server.refuse("c1/-/information", "Server busy", 503)
        model.refresh()
        until("the failure") { failures.isNotEmpty() }
        assertNull(model.uiState.value.error)
        assertFalse(model.uiState.value.isRefreshing)
        assertEquals(MochiError.ServerError(503, "Server busy"), failures.single())
    }

    @Test
    fun `an export whose warm-up fails says so`() {
        val model = board()
        server.refuse("c1/-/data/export/warm", "Warm-up failed", 500)
        model.exportCrm()
        until("the failure") { failures.isNotEmpty() }
        assertEquals(MochiError.ServerError(500, "Warm-up failed"), failures.single())
        assertNull(model.uiState.value.error)
        assertFalse(model.uiState.value.isExporting)
        assertNull(model.uiState.value.pendingExport)
    }
}
