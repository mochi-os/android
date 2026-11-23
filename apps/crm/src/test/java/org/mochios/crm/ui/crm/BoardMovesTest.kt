// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.crm

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.core.content.IntentCompat
import androidx.lifecycle.SavedStateHandle
import com.google.gson.JsonObject
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.android.share.EXTRA_ADDRESS
import org.mochios.android.share.EXTRA_WORDS
import org.mochios.android.share.Shared
import org.mochios.android.share.share
import org.mochios.android.ui.components.dnd.DragEdge
import org.mochios.crm.CrmServer
import org.mochios.crm.R
import org.mochios.crm.lib.ActiveViewStore
import org.mochios.crm.lib.CollapsedColumnsStore
import org.mochios.crm.model.CrmObject
import org.mochios.crm.settle
import org.mochios.crm.ui.board.MoveObjectSheet
import org.mochios.crm.ui.tree.TreeNode
import org.mochios.crm.ui.tree.handleTreeDrop
import org.mochios.crm.until
import org.mochios.android.websocket.MochiWebSocket
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The board's and the tree's moves as the server reads them: the object a
 * card goes after or before, the column's ends among every object it holds,
 * a top-level parent that arrives as "", a column delete only while nothing
 * holds it, and the failures that come after the CRM has loaded said where
 * the user is.
 */
@RunWith(RobolectricTestRunner::class)
// Tall enough for the Move sheet to lay out every place it offers.
@Config(sdk = [35], qualifiers = "w400dp-h1600dp")
class BoardMovesTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: CrmServer
    private lateinit var socket: MochiWebSocket
    private val failures = mutableListOf<MochiError>()
    private val jobs = mutableListOf<Job>()

    // Column "lead" in the server's rank order: c1 (a contact under a, which
    // the view hides), a (company), b (company), d (company). The board shows
    // a, b, d; the server counts c1 too.
    private val objects = """[
        {"id": "a", "class": "company", "parent": "", "rank": "a0", "values": {"stage": "lead", "status": "open"}},
        {"id": "c1", "class": "contact", "parent": "a", "rank": "Z0", "values": {"stage": "lead", "status": "open"}},
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
        server.data("c1/-/share", """{"link": "mochi://12D3KooWPeer/c1"}""")
        socket = server.socket()
    }

    @After
    fun end() {
        jobs.forEach { job -> job.cancel() }
        socket.disconnectAll()
        server.stop()
    }

    private fun board(count: Int = 5): CrmViewModel {
        val model = CrmViewModel(
            SavedStateHandle(mapOf("crmId" to "c1")),
            server.repository(),
            socket,
            server.session,
            ActiveViewStore(context),
            CollapsedColumnsStore(context),
        )
        jobs += CoroutineScope(Dispatchers.Main).launch { model.actionFailed.collect { failures += it } }
        until("the board") { model.uiState.value.objects.size == count && model.uiState.value.crmDetails != null }
        return model
    }

    // Lead with names to tell its cards apart by, in lanes of tier: c1 and x
    // are contacts the company board does not show, x holding lead's highest
    // key, and u is in no column.
    private val named = """[
        {"id": "a", "class": "company", "parent": "", "rank": "a0", "values": {"stage": "lead", "name": "Acme", "tier": "gold"}},
        {"id": "c1", "class": "contact", "parent": "a", "rank": "Z0", "values": {"stage": "lead", "name": "Carol"}},
        {"id": "b", "class": "company", "parent": "", "rank": "a2", "values": {"stage": "lead", "name": "Bolt", "tier": "silver"}},
        {"id": "d", "class": "company", "parent": "", "rank": "a3", "values": {"stage": "lead", "name": "Dyna", "tier": "gold"}},
        {"id": "w", "class": "company", "parent": "", "rank": "a4", "values": {"stage": "won", "name": "Wren"}},
        {"id": "x", "class": "contact", "parent": "", "rank": "a9", "values": {"stage": "lead", "name": "Xavi"}},
        {"id": "u", "class": "company", "parent": "", "rank": "b0", "values": {"stage": "", "name": "Umbra"}}
    ]"""

    /**
     * The CRM with [named]'s objects and any [extra] ones, on a board with
     * lanes of tier when [lanes].
     */
    private fun named(lanes: Boolean = false, extra: List<String> = emptyList()): CrmViewModel {
        val rows = if (lanes) "tier" else ""
        server.data("c1/-/information", """{"crm": {"id": "c1", "name": "Sales", "access": "owner"},
            "classes": [{"id": "company", "name": "Company", "title": "name"}, {"id": "contact", "name": "Contact", "title": "name"}],
            "fields": {"company": [{"id": "stage", "name": "Stage", "fieldtype": "enumerated"}, {"id": "name", "name": "Name", "fieldtype": "text"},
                {"id": "tier", "name": "Tier", "fieldtype": "enumerated"}]},
            "options": {"company": {"stage": [{"id": "lead", "name": "Lead"}, {"id": "won", "name": "Won"}, {"id": "lost", "name": "Lost"}],
                "tier": [{"id": "gold", "name": "Gold"}, {"id": "silver", "name": "Silver"}]}},
            "views": [{"id": "v1", "name": "Board", "viewtype": "board", "columns": "stage", "rows": "$rows", "classes": ["company"]}],
            "hierarchy": {"company": [""], "contact": ["", "company"]}}""")
        val objects = if (extra.isEmpty()) named else named.trimEnd().removeSuffix("]") + ", " + extra.joinToString(", ") + "]"
        server.data("c1/-/objects", """{"objects": $objects, "watched": []}""")
        return board(7 + extra.size)
    }

    private fun ids(objects: List<CrmObject>) = objects.map { obj -> obj.id }

    @Test
    fun `the Move sheet offers the cards beside it in the order the board draws them`() {
        val model = named()
        assertEquals("the contacts the board does not show are left out", listOf("a", "d"), ids(model.movePlaces("b")))
        model.setSortDirection("desc")
        assertEquals("THE POINT: upside down, as the board draws them", listOf("d", "a"), ids(model.movePlaces("b")))
        model.setSortField("created")
        assertEquals("a sort by a field draws no place to go", emptyList<String>(), ids(model.movePlaces("b")))
    }

    @Test
    fun `the Move sheet offers only the cards of its own lane`() {
        val model = named(lanes = true)
        assertEquals("THE POINT: Bolt is drawn in another lane", listOf("d"), ids(model.movePlaces("a")))
    }

    @Test
    fun `under a descending manual sort the top of a column is after its highest key`() {
        val model = named()
        model.setSortDirection("desc")
        model.moveToTop("w", "stage", "lead")
        until("the move") { server.of("c1/-/objects/w/move").isNotEmpty() }
        val top = server.of("c1/-/objects/w/move").single().json()
        assertEquals("THE POINT: the top drawn is past x, the highest key", "x", top.get("after").asString)
        assertFalse(top.has("before"))
    }

    /** The Move sheet open on Bolt under a descending manual sort, its place [text] chosen; answers the move sent. */
    private fun descendingSheet(text: String): JsonObject {
        val model = named()
        server.refuse("c1/-/objects", "Down", 500)
        model.setSortDirection("desc")
        val bolt = model.uiState.value.objects.first { obj -> obj.id == "b" }
        rule.setContent { MoveObjectSheet(bolt, model, onDismiss = {}) }
        rule.waitForIdle()
        rule.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick)
        until("the move") { server.of("c1/-/objects/b/move").isNotEmpty() }
        return server.of("c1/-/objects/b/move").single().json()
    }

    @Test
    fun `under a descending manual sort the Move sheet's After goes below the card as the board draws it`() {
        val after = descendingSheet(context.getString(R.string.crm_move_after, "Acme"))
        assertEquals("THE POINT: drawn below Acme is before it", "a", after.get("before").asString)
        assertFalse(after.has("after"))
    }

    @Test
    fun `under a descending manual sort the Move sheet's Top goes above every card as the board draws them`() {
        val top = descendingSheet(context.getString(R.string.crm_move_position_top))
        assertEquals("THE POINT: the top drawn is past x, the highest key", "x", top.get("after").asString)
        assertFalse(top.has("before"))
    }

    @Test
    fun `a drop on a lane of the column for no value clears the column and ends past the cards drawn there`() {
        val model = named(lanes = true)
        model.moveToEnd("b", "stage", "", rowField = "tier", rowValue = "gold")
        until("the move") { server.of("c1/-/objects/b/move").isNotEmpty() }
        val end = server.of("c1/-/objects/b/move").single().json()
        assertEquals("THE POINT: the move clears the stage, which an empty value alone keeps", "true", end.get("clear").asString)
        assertEquals("", end.get("value").asString)
        assertEquals("THE POINT: after Umbra, the last card the column for no value holds", "u", end.get("after").asString)
        model.setSortDirection("desc")
        model.moveToEnd("d", "stage", "", rowField = "tier", rowValue = "silver")
        until("the second move") { server.of("c1/-/objects/d/move").isNotEmpty() }
        assertEquals("the bottom drawn is below Umbra, its lowest key", "u", server.of("c1/-/objects/d/move").single().json().get("before").asString)
    }

    // Stale, a company whose stage no column is drawn for, in the column for
    // no value with Umbra.
    private val stale = """{"id": "s", "class": "company", "parent": "", "rank": "b5", "values": {"stage": "gone", "name": "Stale"}}"""

    private fun stage(model: CrmViewModel, id: String): String =
        model.uiState.value.objects.first { obj -> obj.id == id }.stringValue("stage")

    @Test
    fun `a card dropped on the column for no value from another clears its stage`() {
        val model = named(extra = listOf(stale))
        server.refuse("c1/-/objects", "Down", 500)
        model.moveBeside("b", target = "u", below = true, field = "stage", value = "")
        assertEquals("THE POINT: drawn in the column for no value at once", "", stage(model, "b"))
        until("the move") { server.of("c1/-/objects/b/move").isNotEmpty() }
        val move = server.of("c1/-/objects/b/move").single().json()
        assertEquals("THE POINT: the move clears the stage", "true", move.get("clear").asString)
        assertEquals("stage", move.get("field").asString)
        assertEquals("u", move.get("after").asString)
    }

    @Test
    fun `a drop at the end of the column for no value goes past every card drawn there`() {
        val model = named(extra = listOf(stale))
        model.moveToEnd("b", "stage", "")
        until("the move") { server.of("c1/-/objects/b/move").isNotEmpty() }
        val end = server.of("c1/-/objects/b/move").single().json()
        assertEquals("THE POINT: after Stale, which the column draws though it holds a stage", "s", end.get("after").asString)
        assertEquals("true", end.get("clear").asString)
    }

    @Test
    fun `a card moved among those drawn in the column for no value keeps its stage`() {
        val model = named(extra = listOf(stale))
        server.refuse("c1/-/objects", "Down", 500)
        model.moveBeside("s", target = "u", below = false, field = "stage", value = "")
        until("the move") { server.of("c1/-/objects/s/move").isNotEmpty() }
        assertFalse("THE POINT: a stage no column is drawn for is not cleared by a reorder", server.of("c1/-/objects/s/move").single().json().has("clear"))
        assertEquals("gone", stage(model, "s"))
        model.moveBeside("u", target = "s", below = true, field = "stage", value = "")
        until("the second move") { server.of("c1/-/objects/u/move").isNotEmpty() }
        assertFalse("nor one with none", server.of("c1/-/objects/u/move").single().json().has("clear"))
    }

    @Test
    fun `under a sort by a field a drop on the column for no value still clears the stage`() {
        val model = named(extra = listOf(stale))
        model.setSortField("created")
        model.moveBeside("b", target = "u", below = true, field = "stage", value = "")
        until("the move") { server.of("c1/-/objects/b/move").isNotEmpty() }
        val move = server.of("c1/-/objects/b/move").single().json()
        assertEquals("THE POINT: the stage changes, so the move goes", "true", move.get("clear").asString)
        assertFalse("with no place, as the board draws none", move.has("after") || move.has("before"))
    }

    @Test
    fun `the Move sheet places a card whose stage no column is drawn for among the cards it is drawn with`() {
        val model = named(extra = listOf(stale))
        server.refuse("c1/-/objects", "Down", 500)
        val card = model.uiState.value.objects.first { obj -> obj.id == "s" }
        rule.setContent { MoveObjectSheet(card, model, onDismiss = {}) }
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(R.string.crm_move_position_top)).performSemanticsAction(SemanticsActions.OnClick)
        until("the move") { server.of("c1/-/objects/s/move").isNotEmpty() }
        val top = server.of("c1/-/objects/s/move").single().json()
        assertEquals("THE POINT: no option the server would refuse, but the column it is drawn in", "", top.get("value").asString)
        assertFalse("which it keeps", top.has("clear"))
        assertEquals("above Umbra, the first card there", "u", top.get("before").asString)
    }

    @Test
    fun `a drop at the end of a column goes after every object it holds`() {
        val model = board()
        model.moveToEnd("w", "stage", "lead", rowField = "status", rowValue = "closed")
        until("the move") { server.of("c1/-/objects/w/move").isNotEmpty() }
        val move = server.of("c1/-/objects/w/move").single().json()
        assertEquals("THE POINT: after d, the last object lead holds", "d", move.get("after").asString)
        assertEquals("lead", move.get("value").asString)
        assertEquals("""{"field":"status","value":"closed"}""", move.get("row").asString)
    }

    @Test
    fun `a move to the top of a column goes above the hidden object at its top too`() {
        val model = board()
        model.moveToTop("w", "stage", "lead")
        until("the move") { server.of("c1/-/objects/w/move").isNotEmpty() }
        val move = server.of("c1/-/objects/w/move").single().json()
        assertEquals("THE POINT: above c1, which the board does not show, not a", "c1", move.get("before").asString)
        assertFalse(move.has("after"))
    }

    /** The ids lead holds, in the order the board draws them. */
    private fun lead(model: CrmViewModel): List<String> =
        model.sortObjects(model.uiState.value.objects.filter { obj -> obj.stringValue("stage") == "lead" }).map { obj -> obj.id }

    @Test
    fun `a moved card is drawn beside its anchor at once and takes the keys the server wrote`() {
        val model = board()
        val answering = CountDownLatch(1)
        server.on("c1/-/objects/w/move") {
            answering.await(10, TimeUnit.SECONDS)
            CrmServer.ok("""{"success": true, "ranks": [{"id": "w", "rank": "a0V", "updated": 99}], "updated": 99}""")
        }
        // A reload changes nothing, so only the answer can give the key.
        server.refuse("c1/-/objects", "Down", 500)
        model.moveObject("w", "stage", "lead", after = "a")
        assertEquals("THE POINT: after a before the server answers", listOf("c1", "a", "w", "b", "d"), lead(model))
        answering.countDown()
        until("the keys") { model.uiState.value.objects.first { obj -> obj.id == "w" }.rank == "a0V" }
        assertEquals(listOf("c1", "a", "w", "b", "d"), lead(model))
        assertEquals(99L, model.uiState.value.objects.first { obj -> obj.id == "w" }.updated)
    }

    @Test
    fun `a move that fails puts the card back, though the reload fails too`() {
        val model = board()
        server.refuse("c1/-/objects/w/move", "Refused", 500)
        server.refuse("c1/-/objects", "Down", 500)
        model.moveObject("w", "stage", "lead", after = "a")
        assertEquals("drawn where it was dropped", "lead", model.uiState.value.objects.first { obj -> obj.id == "w" }.stringValue("stage"))
        until("the failure") { failures.isNotEmpty() }
        val back = model.uiState.value.objects.first { obj -> obj.id == "w" }
        assertEquals("THE POINT: in its own column again", "won", back.stringValue("stage"))
        assertEquals("a4", back.rank)
    }

    @Test
    fun `under a descending manual sort a card dropped below another goes before it`() {
        val model = board()
        server.refuse("c1/-/objects", "Down", 500)
        model.setSortDirection("desc")
        model.moveBeside("w", target = "a", below = true, field = "stage", value = "lead")
        until("the move") { server.of("c1/-/objects/w/move").isNotEmpty() }
        val move = server.of("c1/-/objects/w/move").single().json()
        assertEquals("THE POINT: keys drawn upside down put below before", "a", move.get("before").asString)
        assertFalse(move.has("after"))
        assertEquals("drawn below a at once", listOf("d", "b", "a", "w", "c1"), lead(model))
    }

    @Test
    fun `under a descending manual sort the end of a column is before its lowest key`() {
        val model = board()
        model.setSortDirection("desc")
        model.moveToEnd("w", "stage", "lead")
        until("the move") { server.of("c1/-/objects/w/move").isNotEmpty() }
        val end = server.of("c1/-/objects/w/move").single().json()
        assertEquals("THE POINT: the end drawn is the bottom, below c1, the lowest key", "c1", end.get("before").asString)
        assertFalse(end.has("after"))
    }

    @Test
    fun `under a sort by a field a drop names no neighbour, and one that changes nothing writes nothing`() {
        val model = board()
        model.setSortField("created")
        model.moveBeside("w", target = "a", below = true, field = "stage", value = "lead")
        until("the move") { server.of("c1/-/objects/w/move").isNotEmpty() }
        val move = server.of("c1/-/objects/w/move").single().json()
        assertFalse("THE POINT: the board shows no place, so none is named", move.has("after") || move.has("before"))
        assertEquals("lead", move.get("value").asString)
        model.moveBeside("b", target = "a", below = true, field = "stage", value = "lead")
        model.moveToEnd("a", "stage", "lead")
        settle()
        assertTrue(server.of("c1/-/objects/b/move").isEmpty() && server.of("c1/-/objects/a/move").isEmpty())
    }

    @Test
    fun `the tree places by an edge only under a manual sort, upside down when it descends`() {
        val model = board()
        val target = TreeNode(model.uiState.value.objects.first { it.id == "b" }, depth = 0, hasChildren = false, isExpanded = true)
        model.setSortField("created")
        handleTreeDrop(sourceId = "w", targetNode = target, edge = DragEdge.Top, viewModel = model)
        handleTreeDrop(sourceId = "c1", targetNode = target, edge = DragEdge.Bottom, viewModel = model)
        settle()
        assertTrue("THE POINT: a sort by when it was made draws no place beside a row", server.sent.none { it.path.endsWith("/move") })
        model.setSortField("rank")
        model.setSortDirection("desc")
        handleTreeDrop(sourceId = "w", targetNode = target, edge = DragEdge.Bottom, viewModel = model)
        until("the move") { server.of("c1/-/objects/w/move").isNotEmpty() }
        assertEquals("below b upside down is before it", "b", server.of("c1/-/objects/w/move").single().json().get("before").asString)
    }

    @Test
    fun `a move to an empty column names no neighbour`() {
        val model = board()
        model.moveToEnd("w", "stage", "lost")
        until("the move") { server.of("c1/-/objects/w/move").isNotEmpty() }
        val move = server.of("c1/-/objects/w/move").single().json()
        assertFalse(move.has("after") || move.has("before"))
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
    fun `top-level rows reorder beside the row dropped on`() {
        val model = board()
        val target = TreeNode(model.uiState.value.objects.first { it.id == "b" }, depth = 0, hasChildren = false, isExpanded = true)
        handleTreeDrop(sourceId = "w", targetNode = target, edge = DragEdge.Bottom, viewModel = model)
        until("the move") { server.of("c1/-/objects/w/move").isNotEmpty() }
        val move = server.of("c1/-/objects/w/move").single().json()
        assertEquals("b", move.get("after").asString)
        assertFalse("it stays at the top level, so no parent goes", move.has("parent"))
    }

    @Test
    fun `a row dropped beside a row of another parent moves there in one move`() {
        val model = board()
        val target = TreeNode(model.uiState.value.objects.first { it.id == "b" }, depth = 0, hasChildren = false, isExpanded = true)
        handleTreeDrop(sourceId = "c1", targetNode = target, edge = DragEdge.Top, viewModel = model)
        until("the move") { server.of("c1/-/objects/c1/move").isNotEmpty() }
        val move = server.of("c1/-/objects/c1/move").single().json()
        assertEquals("THE POINT: the new parent and the place above b together", "", move.get("parent").asString)
        assertEquals("b", move.get("before").asString)
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
    fun `the CRM's Share hands other apps the link it is joined by, and Mochi the CRM by name and address`() {
        val model = board()
        var shared: Shared? = null
        jobs += CoroutineScope(Dispatchers.Main).launch { model.shares.collect { shared = it } }
        model.share()
        until("the share") { shared != null }
        assertTrue(server.of("c1/-/share").isNotEmpty())
        // The sheet the screen's ShareSheet opens with it.
        share(context, shared!!)
        val chooser = shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals("mochi://12D3KooWPeer/c1", send.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("Sales", send.getStringExtra(EXTRA_WORDS))
        assertEquals(server.session.getServerUrlBlocking().trimEnd('/') + "/crm/c1", send.getStringExtra(EXTRA_ADDRESS))
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
