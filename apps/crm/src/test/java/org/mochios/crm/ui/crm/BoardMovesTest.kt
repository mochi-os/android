// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.crm

import android.content.Intent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.core.content.IntentCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.mockwebserver.MockResponse
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
import org.mochios.crm.ui.board.BoardView
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
     * lanes of tier when [lanes]; with [prospects], a contact's stage offers
     * prospect, which a company's does not.
     */
    private fun named(lanes: Boolean = false, extra: List<String> = emptyList(), prospects: Boolean = false): CrmViewModel {
        val rows = if (lanes) "tier" else ""
        val contact = if (prospects) """, "contact": [{"id": "stage", "name": "Stage", "fieldtype": "enumerated"}]""" else ""
        val offered = if (prospects) """, "contact": {"stage": [{"id": "lead", "name": "Lead"}, {"id": "prospect", "name": "Prospect"}]}""" else ""
        server.data("c1/-/information", """{"crm": {"id": "c1", "name": "Sales", "access": "owner"},
            "classes": [{"id": "company", "name": "Company", "title": "name"}, {"id": "contact", "name": "Contact", "title": "name"}],
            "fields": {"company": [{"id": "stage", "name": "Stage", "fieldtype": "enumerated"}, {"id": "name", "name": "Name", "fieldtype": "text"},
                {"id": "tier", "name": "Tier", "fieldtype": "enumerated"}]$contact},
            "options": {"company": {"stage": [{"id": "lead", "name": "Lead"}, {"id": "won", "name": "Won"}, {"id": "lost", "name": "Lost"}],
                "tier": [{"id": "gold", "name": "Gold"}, {"id": "silver", "name": "Silver"}]}$offered},
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

    // Vale, a gold company in the column for no value, above Umbra.
    private val vale = """{"id": "v", "class": "company", "parent": "", "rank": "a5", "values": {"stage": "", "name": "Vale", "tier": "gold"}}"""

    @Test
    fun `a drop on a lane of the column for no value clears the column and ends past the cards drawn in that lane`() {
        val model = named(lanes = true, extra = listOf(vale))
        model.moveToEnd("b", "stage", "", rowField = "tier", rowValue = "gold")
        until("the move") { server.of("c1/-/objects/b/move").isNotEmpty() }
        val end = server.of("c1/-/objects/b/move").single().json()
        assertEquals("THE POINT: the move clears the stage, which an empty value alone keeps", "true", end.get("clear").asString)
        assertEquals("", end.get("value").asString)
        assertEquals("THE POINT: after Vale, the last card of gold there, as the server takes a neighbour in the lane", "v", end.get("after").asString)
        model.setSortDirection("desc")
        model.moveToEnd("d", "stage", "", rowField = "tier", rowValue = "")
        until("the second move") { server.of("c1/-/objects/d/move").isNotEmpty() }
        assertEquals("the bottom of the lane for none is below Umbra, its lowest key", "u", server.of("c1/-/objects/d/move").single().json().get("before").asString)
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
        assertFalse(
            "THE POINT: the column it stays in is not named, so the server takes Umbra, whose stage is not Stale's, beside it",
            top.has("field") || top.has("value"),
        )
        assertFalse("and it keeps its stage", top.has("clear"))
        assertEquals("above Umbra, the first card there", "u", top.get("before").asString)
    }

    @Test
    fun `a card reordered in the column for no value is drawn beside a card whose stage is not its own`() {
        val model = named(extra = listOf(stale))
        server.refuse("c1/-/objects", "Down", 500)
        val answering = CountDownLatch(1)
        server.on("c1/-/objects/u/move") {
            answering.await(10, TimeUnit.SECONDS)
            CrmServer.ok("{}")
        }
        // Umbra dropped below Stale, which holds a stage no column is drawn for.
        model.moveBeside("u", target = "s", below = true, field = "stage", value = "")
        val none = model.sortObjects(model.uiState.value.objects.filter { obj -> obj.stringValue("stage") !in setOf("lead", "won", "lost") })
        assertEquals("THE POINT: below Stale at once, as the server places it", listOf("s", "u"), ids(none))
        answering.countDown()
        until("the move") { server.of("c1/-/objects/u/move").isNotEmpty() }
        assertFalse(server.of("c1/-/objects/u/move").single().json().has("field"))
    }

    @Test
    fun `a card of a class without the column field is reordered without naming it`() {
        // A note has no stage field, so it is drawn in the column for no value.
        val note = """{"id": "n", "class": "note", "parent": "", "rank": "c0", "values": {"name": "Note"}}"""
        val model = named(extra = listOf(stale, note))
        server.refuse("c1/-/objects", "Down", 500)
        model.moveBeside("n", target = "u", below = true, field = "stage", value = "")
        until("the move") { server.of("c1/-/objects/n/move").isNotEmpty() }
        val move = server.of("c1/-/objects/n/move").single().json()
        assertFalse("THE POINT: named, the server refuses the move, the field not found", move.has("field"))
        assertEquals("u", move.get("after").asString)
    }

    /** The ids the column for no value draws, in the order the board draws them. */
    private fun none(model: CrmViewModel): List<String> =
        ids(model.sortObjects(model.uiState.value.objects.filter { obj -> obj.stringValue("stage") !in setOf("lead", "won", "lost") }))

    // Opal, with no stage, past Stale: the column for no value's last card.
    private val opal = """{"id": "o", "class": "company", "parent": "", "rank": "c5", "values": {"stage": "", "name": "Opal"}}"""

    /** Bolt cleared on the column for no value, dropped below [after]; asserts it drawn there, of the ids [drawn] gives, at once. */
    private fun clearBelow(model: CrmViewModel, after: String, message: String, expected: List<String>, drawn: (CrmViewModel) -> List<String> = this::none, field: String = "stage") {
        server.refuse("c1/-/objects", "Down", 500)
        val answering = CountDownLatch(1)
        server.on("c1/-/objects/b/move") {
            answering.await(10, TimeUnit.SECONDS)
            CrmServer.ok("{}")
        }
        model.moveObject("b", field, "", after = after, clear = true)
        assertEquals(message, expected, drawn(model))
        answering.countDown()
        until("the move") { server.of("c1/-/objects/b/move").isNotEmpty() }
        val move = server.of("c1/-/objects/b/move").single().json()
        assertEquals(after, move.get("after").asString)
        assertEquals("true", move.get("clear").asString)
    }

    @Test
    fun `a card cleared on the column for no value is drawn beside a card whose stage is a deleted option's`() {
        val model = named(extra = listOf(stale, opal))
        clearBelow(model, "s", "THE POINT: below Stale at once, as the server places it", listOf("u", "s", "b", "o"))
    }

    @Test
    fun `a card cleared on the column for no value is drawn beside a card whose stage only another class offers`() {
        // Pia, a contact in Prospect, which a company's stage does not offer.
        val pia = """{"id": "p", "class": "contact", "parent": "", "rank": "b7", "values": {"stage": "prospect", "name": "Pia"}}"""
        val model = named(extra = listOf(stale, pia, opal), prospects = true)
        clearBelow(model, "p", "THE POINT: below Pia at once, as the server places it", listOf("u", "s", "p", "b", "o"))
    }

    @Test
    fun `a card cleared on the column for no value is not drawn beside a card holding an option of its own class`() {
        val model = named(extra = listOf(stale, opal))
        // Wren, named after, is in Won.
        clearBelow(model, "w", "THE POINT: at the column's end, as the server places it with no neighbour there", listOf("u", "s", "o", "b"))
    }

    @Test
    fun `a card cleared of a field its class holds as text is not drawn beside a card holding a value of it`() {
        val model = named(extra = listOf(stale, opal))
        clearBelow(
            model, "s", "THE POINT: after every key, as the server places it, a text field having no column for no value",
            listOf("c1", "a", "d", "w", "x", "u", "s", "o", "b"), { drawn -> ids(drawn.sortObjects(drawn.uiState.value.objects)) },
            field = "name",
        )
    }

    @Test
    fun `a drop at the end of a column goes after every object it holds`() {
        val model = board()
        model.moveToEnd("w", "stage", "lead")
        until("the move") { server.of("c1/-/objects/w/move").isNotEmpty() }
        val move = server.of("c1/-/objects/w/move").single().json()
        assertEquals("THE POINT: after d, the last object lead holds", "d", move.get("after").asString)
        assertEquals("lead", move.get("value").asString)
        assertFalse(move.has("row"))
    }

    @Test
    fun `a drop at the end of a column in another lane goes after the last object of that lane`() {
        val model = board()
        model.moveToEnd("w", "stage", "lead", rowField = "status", rowValue = "closed")
        until("the move") { server.of("c1/-/objects/w/move").isNotEmpty() }
        val move = server.of("c1/-/objects/w/move").single().json()
        assertEquals("THE POINT: after b, lead's last closed one, not d, which the server takes in no other lane", "b", move.get("after").asString)
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
        // Held until the drop is seen drawn: a refusal answered at once can
        // resume the move before the call that made it returns.
        val answering = CountDownLatch(1)
        server.on("c1/-/objects/w/move") {
            answering.await(10, TimeUnit.SECONDS)
            MockResponse().setResponseCode(500).setBody("""{"error": "Refused"}""")
        }
        server.refuse("c1/-/objects", "Down", 500)
        model.moveObject("w", "stage", "lead", after = "a")
        assertEquals("drawn where it was dropped", "lead", model.uiState.value.objects.first { obj -> obj.id == "w" }.stringValue("stage"))
        answering.countDown()
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

    // ---- Both neighbours ----

    @Test
    fun `a card dropped between two others names both, as the board draws them`() {
        val model = named()
        server.refuse("c1/-/objects", "Down", 500)
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
        val dyna = rule.onNodeWithText("Dyna").fetchSemanticsNode().boundsInRoot
        rule.onRoot().performTouchInput {
            down(dyna.center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(Offset(0f, 4f))
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        val bolt = rule.onNodeWithText("Bolt").fetchSemanticsNode().boundsInRoot
        rule.onRoot().performTouchInput {
            moveTo(Offset(bolt.center.x, bolt.top + 1f))
            up()
        }
        until("the move") { server.of("c1/-/objects/d/move").isNotEmpty() }
        val move = server.of("c1/-/objects/d/move").single().json()
        assertEquals("above Bolt", "b", move.get("before").asString)
        assertEquals("THE POINT: and below Acme, the card drawn above the drop", "a", move.get("after").asString)
        assertFalse("THE POINT: the column it stays in is not named", move.has("field") || move.has("value"))
    }

    @Test
    fun `the Move sheet's After names the card drawn below it too`() {
        val model = named()
        server.refuse("c1/-/objects", "Down", 500)
        val bolt = model.uiState.value.objects.first { obj -> obj.id == "b" }
        rule.setContent { MoveObjectSheet(bolt, model, onDismiss = {}) }
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(R.string.crm_move_after, "Acme")).performSemanticsAction(SemanticsActions.OnClick)
        until("the move") { server.of("c1/-/objects/b/move").isNotEmpty() }
        val move = server.of("c1/-/objects/b/move").single().json()
        assertEquals("a", move.get("after").asString)
        assertEquals("THE POINT: and before Dyna, drawn below Acme", "d", move.get("before").asString)
    }

    @Test
    fun `under a descending manual sort tied cards are drawn in the server's order upside down, and a drop between them names both`() {
        // Tide shares Bolt's key; the server lists Bolt, the newer, first.
        val tide = """{"id": "t", "class": "company", "parent": "", "rank": "a2", "values": {"stage": "lead", "name": "Tide"}}"""
        val model = named(extra = listOf(tide))
        server.refuse("c1/-/objects", "Down", 500)
        model.setSortDirection("desc")
        assertEquals("THE POINT: the exact reverse of the server's order, ties too", listOf("x", "d", "t", "b", "a", "c1"), lead(model))
        // Dropped below Tide as drawn, so above Bolt.
        model.moveBeside("w", target = "t", below = true, field = "stage", value = "lead", other = "b")
        until("the move") { server.of("c1/-/objects/w/move").isNotEmpty() }
        val move = server.of("c1/-/objects/w/move").single().json()
        assertEquals("below Tide upside down is before it", "t", move.get("before").asString)
        assertEquals("THE POINT: and after Bolt, which the server unties below Tide", "b", move.get("after").asString)
        assertEquals("drawn between them at once", listOf("x", "d", "t", "w", "b", "a", "c1"), lead(model))
    }

    @Test
    fun `a move is drawn beside its other neighbour when the first has left the column`() {
        val model = named()
        val answering = CountDownLatch(1)
        server.on("c1/-/objects/w/move") {
            answering.await(10, TimeUnit.SECONDS)
            CrmServer.ok("{}")
        }
        // Umbra, named after, is in no column; Bolt, named before, is in lead.
        model.moveObject("w", "stage", "lead", after = "u", before = "b")
        assertEquals("THE POINT: above Bolt, as the server places it", listOf("c1", "a", "w", "b", "d", "x"), lead(model))
        answering.countDown()
        until("the move") { server.of("c1/-/objects/w/move").isNotEmpty() }
        val move = server.of("c1/-/objects/w/move").single().json()
        assertEquals("u", move.get("after").asString)
        assertEquals("b", move.get("before").asString)
    }

    @Test
    fun `a tree row dropped between two siblings names both`() {
        val model = board()
        val target = TreeNode(model.uiState.value.objects.first { it.id == "b" }, depth = 0, hasChildren = false, isExpanded = true)
        handleTreeDrop(sourceId = "w", targetNode = target, edge = DragEdge.Top, viewModel = model)
        until("the move") { server.of("c1/-/objects/w/move").isNotEmpty() }
        val move = server.of("c1/-/objects/w/move").single().json()
        assertEquals("above b", "b", move.get("before").asString)
        assertEquals("THE POINT: and below a, the sibling drawn above it", "a", move.get("after").asString)
    }

    // ---- The lane for none ----

    // Vast's tier no lane is drawn for, so it is drawn in lead's lane for none, with Nil, which has none.
    private val vast = """{"id": "v", "class": "company", "parent": "", "rank": "a5", "values": {"stage": "lead", "name": "Vast", "tier": "bronze"}}"""
    private val nil = """{"id": "n", "class": "company", "parent": "", "rank": "a6", "values": {"stage": "lead", "name": "Nil"}}"""

    @Test
    fun `a card reordered in the lane for none keeps a tier no lane is drawn for`() {
        val model = named(lanes = true, extra = listOf(vast, nil))
        server.refuse("c1/-/objects", "Down", 500)
        val answering = CountDownLatch(1)
        server.on("c1/-/objects/v/move") {
            answering.await(10, TimeUnit.SECONDS)
            CrmServer.ok("{}")
        }
        model.moveBeside("v", target = "n", below = true, field = "stage", value = "lead", rowField = "tier", rowValue = "")
        val none = model.sortObjects(model.uiState.value.objects.filter { obj -> obj.id in setOf("v", "n") })
        assertEquals("THE POINT: drawn below Nil at once, as the server places it", listOf("n", "v"), ids(none))
        answering.countDown()
        until("the move") { server.of("c1/-/objects/v/move").isNotEmpty() }
        assertFalse(
            "THE POINT: it stays in the lane it is drawn in, so no lane is named: its own would refuse Nil beside it, and \"\" would clear it",
            server.of("c1/-/objects/v/move").single().json().has("row"),
        )
        assertEquals("bronze", model.uiState.value.objects.first { obj -> obj.id == "v" }.stringValue("tier"))
        model.moveBeside("a", target = "n", below = true, field = "stage", value = "lead", rowField = "tier", rowValue = "")
        until("the second move") { server.of("c1/-/objects/a/move").isNotEmpty() }
        assertEquals("one from gold has its tier cleared", """{"field":"tier","value":""}""", server.of("c1/-/objects/a/move").single().json().get("row").asString)
    }

    @Test
    fun `under a sort by a field a reorder in the lane for none writes nothing`() {
        val model = named(lanes = true, extra = listOf(vast, nil))
        model.setSortField("created")
        model.moveBeside("v", target = "n", below = true, field = "stage", value = "lead", rowField = "tier", rowValue = "")
        model.moveToEnd("v", "stage", "lead", rowField = "tier", rowValue = "")
        settle()
        assertTrue("THE POINT: nothing changes, so nothing goes", server.of("c1/-/objects/v/move").isEmpty())
    }

    @Test
    fun `a card dropped in the lane for none is drawn beside a card whose tier no option has`() {
        val model = named(lanes = true, extra = listOf(vast, nil))
        server.refuse("c1/-/objects", "Down", 500)
        val answering = CountDownLatch(1)
        server.on("c1/-/objects/d/move") {
            answering.await(10, TimeUnit.SECONDS)
            CrmServer.ok("{}")
        }
        // Dyna, in gold, dropped below Vast, whose tier no lane is drawn for.
        model.moveBeside("d", target = "v", below = true, field = "stage", value = "lead", rowField = "tier", rowValue = "")
        assertEquals("THE POINT: below Vast at once, as the server places it", listOf("c1", "a", "b", "v", "d", "n", "x"), lead(model))
        answering.countDown()
        until("the move") { server.of("c1/-/objects/d/move").isNotEmpty() }
        val move = server.of("c1/-/objects/d/move").single().json()
        assertEquals("v", move.get("after").asString)
        assertEquals("""{"field":"tier","value":""}""", move.get("row").asString)
    }

    // ---- What a move goes among ----

    @Test
    fun `a drop at the end of its own column on a board with lanes names neither, and ends in the lane it stays in`() {
        val model = named(lanes = true)
        server.refuse("c1/-/objects", "Down", 500)
        // On the column's empty space, which names no lane.
        model.moveToEnd("a", "stage", "lead")
        until("the move") { moves("a").isNotEmpty() }
        val move = moves("a").single()
        assertFalse("THE POINT: the column does not change, so it is not named", move.has("field"))
        assertFalse("THE POINT: nor does the lane", move.has("row"))
        assertEquals("the end is gold's, after Dyna, not Xavi", "d", move.get("after").asString)
    }

    @Test
    fun `the Move sheet on a board with lanes names only what was chosen`() {
        val model = named(lanes = true)
        server.refuse("c1/-/objects", "Down", 500)
        val acme = model.uiState.value.objects.first { obj -> obj.id == "a" }
        rule.setContent { MoveObjectSheet(acme, model, onDismiss = {}) }
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(R.string.crm_move_after, "Dyna")).performSemanticsAction(SemanticsActions.OnClick)
        until("the move") { moves("a").isNotEmpty() }
        val after = moves("a").single()
        assertFalse("THE POINT: a place in its own column does not name it", after.has("field"))
        assertFalse("THE POINT: nor its own lane", after.has("row"))
        assertEquals("d", after.get("after").asString)
        rule.onNodeWithText("Silver").performSemanticsAction(SemanticsActions.OnClick)
        until("the second move") { moves("a").size == 2 }
        val lane = moves("a")[1]
        assertFalse("THE POINT: a lane chosen names no column", lane.has("field") || lane.has("value"))
        assertEquals("""{"field":"tier","value":"silver"}""", lane.get("row").asString)
    }

    @Test
    fun `a tree row reordered among its siblings names their parent as its scope`() {
        // Cleo is a second contact under Acme, after Carol.
        val cleo = """{"id": "c2", "class": "contact", "parent": "a", "rank": "Z5", "values": {"stage": "lead", "name": "Cleo"}}"""
        val model = named(extra = listOf(cleo))
        server.refuse("c1/-/objects", "Down", 500)
        val bolt = TreeNode(model.uiState.value.objects.first { it.id == "b" }, depth = 0, hasChildren = false, isExpanded = true)
        handleTreeDrop(sourceId = "w", targetNode = bolt, edge = DragEdge.Top, viewModel = model)
        until("the move") { moves("w").isNotEmpty() }
        assertEquals("THE POINT: the top level, as \"\"", "", moves("w").single().get("scope").asString)
        assertFalse("its parent stays, so none goes", moves("w").single().has("parent"))
        val carol = TreeNode(model.uiState.value.objects.first { it.id == "c1" }, depth = 1, hasChildren = false, isExpanded = true)
        handleTreeDrop(sourceId = "c2", targetNode = carol, edge = DragEdge.Top, viewModel = model, drawn = model.uiState.value.objects)
        until("the second move") { moves("c2").isNotEmpty() }
        assertEquals("THE POINT: Acme's children", "a", moves("c2").single().get("scope").asString)
        assertEquals("above Carol", "c1", moves("c2").single().get("before").asString)
        assertFalse(moves("c2").single().has("parent"))
    }

    // ---- A tree whose parents a filter hides ----

    /**
     * The CRM with Corp filtered out: its contacts, Kay and Lee, are drawn at
     * the top level between Top and Last, a contact the hierarchy lets go
     * under Corp, beside Parent and its Kid. Answers the tree's rows in the
     * order drawn.
     */
    private fun hidden(): Pair<CrmViewModel, List<CrmObject>> {
        server.data("c1/-/objects", """{"objects": [
            {"id": "co", "class": "company", "parent": "", "rank": "a0", "values": {"stage": "lead", "name": "Corp"}},
            {"id": "k1", "class": "contact", "parent": "co", "rank": "a1", "values": {"name": "Kay"}},
            {"id": "t1", "class": "company", "parent": "", "rank": "a2", "values": {"stage": "lead", "name": "Top"}},
            {"id": "k2", "class": "contact", "parent": "co", "rank": "a3", "values": {"name": "Lee"}},
            {"id": "t2", "class": "contact", "parent": "", "rank": "a4", "values": {"name": "Last"}},
            {"id": "p", "class": "company", "parent": "", "rank": "a5", "values": {"stage": "lead", "name": "Parent"}},
            {"id": "kid", "class": "contact", "parent": "p", "rank": "a6", "values": {"name": "Kid"}}
        ], "watched": []}""")
        val model = board(7)
        server.refuse("c1/-/objects", "Down", 500)
        return model to model.uiState.value.objects.filter { obj -> obj.id != "co" }
    }

    private fun node(model: CrmViewModel, id: String) =
        TreeNode(model.uiState.value.objects.first { obj -> obj.id == id }, depth = 0, hasChildren = false, isExpanded = true)

    @Test
    fun `a row dropped beside a row whose parent is hidden stays where it is in the tree, between the rows drawn there`() {
        val (model, drawn) = hidden()
        handleTreeDrop(sourceId = "t2", targetNode = node(model, "k1"), edge = DragEdge.Bottom, viewModel = model, drawn = drawn)
        until("the move") { moves("t2").isNotEmpty() }
        val move = moves("t2").single()
        assertFalse("THE POINT: not put under Corp, which the tree does not draw", move.has("parent"))
        assertEquals("after Kay", "k1", move.get("after").asString)
        assertEquals("THE POINT: and before Top, the row drawn below Kay, not Lee, Corp's next", "t1", move.get("before").asString)
        assertFalse("THE POINT: the top level draws rows of two parents, so none is named", move.has("scope"))
    }

    @Test
    fun `a row whose parent is hidden dropped beside a top-level row keeps its parent`() {
        val (model, drawn) = hidden()
        handleTreeDrop(sourceId = "k2", targetNode = node(model, "t1"), edge = DragEdge.Top, viewModel = model, drawn = drawn)
        until("the move") { moves("k2").isNotEmpty() }
        val move = moves("k2").single()
        assertFalse("THE POINT: it is drawn at the top level already, so it stays with Corp", move.has("parent"))
        assertFalse("THE POINT: and the top level, of two parents, names none", move.has("scope"))
        assertEquals("t1", move.get("before").asString)
        assertEquals("Kay, drawn above Top", "k1", move.get("after").asString)
    }

    @Test
    fun `a row reordered after the last child of a hidden parent goes before the row drawn below, not to the end`() {
        val (model, drawn) = hidden()
        handleTreeDrop(sourceId = "k1", targetNode = node(model, "k2"), edge = DragEdge.Bottom, viewModel = model, drawn = drawn)
        until("the move") { moves("k1").isNotEmpty() }
        val move = moves("k1").single()
        assertFalse("THE POINT: named, Corp's last child would be the scope's end, and Kay would jump past every key", move.has("scope"))
        assertEquals("k2", move.get("after").asString)
        assertEquals("Last, drawn below Lee", "t2", move.get("before").asString)
    }

    @Test
    fun `a row under a drawn parent dropped beside a row whose parent is hidden is refused`() {
        val (model, drawn) = hidden()
        handleTreeDrop(sourceId = "kid", targetNode = node(model, "k1"), edge = DragEdge.Top, viewModel = model, drawn = drawn)
        settle()
        assertTrue("THE POINT: it would go under Corp, out of sight", moves("kid").isEmpty())
    }

    /** A CRM whose contacts have a stage too: Parent won, with Kin; Mover in lead, with Under. */
    private fun staged(): CrmViewModel {
        server.data("c1/-/information", """{"crm": {"id": "c1", "name": "Sales", "access": "owner"},
            "classes": [{"id": "company", "name": "Company", "title": "name"}, {"id": "contact", "name": "Contact", "title": "name"}],
            "fields": {"company": [{"id": "stage", "name": "Stage", "fieldtype": "enumerated"}, {"id": "tier", "name": "Tier", "fieldtype": "enumerated"}],
                "contact": [{"id": "stage", "name": "Stage", "fieldtype": "enumerated"}]},
            "options": {"company": {"stage": [{"id": "lead", "name": "Lead"}, {"id": "won", "name": "Won"}], "tier": [{"id": "gold", "name": "Gold"}]},
                "contact": {"stage": [{"id": "lead", "name": "Lead"}, {"id": "won", "name": "Won"}]}},
            "views": [{"id": "v1", "name": "Board", "viewtype": "board", "columns": "stage", "rows": "tier", "classes": ["company"]}],
            "hierarchy": {"company": [""], "contact": ["", "company", "contact"]}}""")
        server.data("c1/-/objects", """{"objects": [
            {"id": "p", "class": "company", "parent": "", "rank": "a0", "values": {"stage": "won", "tier": "gold", "name": "Parent"}},
            {"id": "q", "class": "contact", "parent": "p", "rank": "a1", "values": {"stage": "won", "name": "Kin"}},
            {"id": "x", "class": "contact", "parent": "", "rank": "a2", "values": {"stage": "lead", "name": "Mover"}},
            {"id": "y", "class": "contact", "parent": "x", "rank": "a3", "values": {"stage": "lead", "name": "Under"}}
        ], "watched": []}""")
        val model = board(4)
        server.refuse("c1/-/objects", "Down", 500)
        return model
    }

    @Test
    fun `a row moved into a parent's children takes its board values at once, with its own children`() {
        val model = staged()
        val answering = CountDownLatch(1)
        server.on("c1/-/objects/x/move") {
            answering.await(10, TimeUnit.SECONDS)
            CrmServer.ok("{}")
        }
        val kin = TreeNode(model.uiState.value.objects.first { obj -> obj.id == "q" }, depth = 1, hasChildren = false, isExpanded = true)
        handleTreeDrop(sourceId = "x", targetNode = kin, edge = DragEdge.Bottom, viewModel = model, drawn = model.uiState.value.objects)
        val drawn = model.uiState.value.objects.associateBy { obj -> obj.id }
        assertEquals("p", drawn.getValue("x").parent)
        assertEquals("THE POINT: Parent's stage, before the server answers", "won", drawn.getValue("x").stringValue("stage"))
        assertEquals("THE POINT: and its child's", "won", drawn.getValue("y").stringValue("stage"))
        assertEquals("a contact has no tier, so it takes none", "", drawn.getValue("x").stringValue("tier"))
        answering.countDown()
        until("the move") { moves("x").isNotEmpty() }
        assertEquals("p", moves("x").single().get("parent").asString)
    }

    @Test
    fun `a row dropped on another takes its new parent's board values with its children`() {
        val model = staged()
        server.data("c1/-/objects/x/update", """{"success": true, "parent": "p", "ranks": [{"id": "x", "rank": "a1V", "updated": 77}], "updated": 77}""")
        val parent = TreeNode(model.uiState.value.objects.first { obj -> obj.id == "p" }, depth = 0, hasChildren = true, isExpanded = true)
        handleTreeDrop(sourceId = "x", targetNode = parent, edge = DragEdge.On, viewModel = model, drawn = model.uiState.value.objects)
        until("the key") { model.uiState.value.objects.first { obj -> obj.id == "x" }.rank == "a1V" }
        val drawn = model.uiState.value.objects.associateBy { obj -> obj.id }
        assertEquals("p", drawn.getValue("x").parent)
        assertEquals("THE POINT: Parent's stage, as the server gives it, though the list cannot be read again", "won", drawn.getValue("x").stringValue("stage"))
        assertEquals("THE POINT: and its child's", "won", drawn.getValue("y").stringValue("stage"))
    }

    // ---- One move at a time ----

    private fun moves(id: String) = server.of("c1/-/objects/$id/move").map { sent -> sent.json() }

    @Test
    fun `moves still waiting when the board closes are sent, in turn`() {
        val store = ViewModelStore()
        val model = ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CrmViewModel(
                SavedStateHandle(mapOf("crmId" to "c1")),
                server.repository(),
                socket,
                server.session,
                ActiveViewStore(context),
                CollapsedColumnsStore(context),
            ) as T
        })[CrmViewModel::class.java]
        until("the board") { model.uiState.value.objects.size == 5 && model.uiState.value.crmDetails != null }
        server.refuse("c1/-/objects", "Down", 500)
        val answering = CountDownLatch(1)
        server.on("c1/-/objects/w/move") {
            answering.await(10, TimeUnit.SECONDS)
            CrmServer.ok("{}")
        }
        model.moveObject("w", "stage", "lead", after = "a")
        model.moveObject("b", "stage", "won", after = "w")
        until("the first move") { moves("w").isNotEmpty() }
        // Back, while the first is still being written.
        store.clear()
        answering.countDown()
        until("the second move") { moves("b").isNotEmpty() }
        assertEquals("THE POINT: the second is sent once the first is answered, though the board has gone", "w", moves("b").single().get("after").asString)
    }

    @Test
    fun `a list read while a move is answered lands without taking it back`() {
        val model = board()
        val answering = CountDownLatch(1)
        server.on("c1/-/objects/w/move") {
            answering.await(10, TimeUnit.SECONDS)
            CrmServer.ok("""{"success": true, "ranks": [{"id": "w", "rank": "a0V", "updated": 99}], "updated": 99}""")
        }
        val reading = CountDownLatch(1)
        // The list as it was before the move, held until released.
        server.on("c1/-/objects") {
            reading.await(10, TimeUnit.SECONDS)
            CrmServer.ok("""{"objects": $objects, "watched": []}""")
        }
        model.moveObject("w", "stage", "lead", after = "a")
        until("the move") { moves("w").isNotEmpty() }
        // A pull to refresh, read before the move is written.
        model.refresh()
        until("the reload") { server.of("c1/-/objects").size >= 2 }
        answering.countDown()
        until("the keys") { model.uiState.value.objects.first { obj -> obj.id == "w" }.rank == "a0V" }
        // Every list read from now on fails, so only the stale one lands.
        server.refuse("c1/-/objects", "Down", 500)
        reading.countDown()
        until("the reload to land") { !model.uiState.value.isRefreshing }
        settle()
        assertEquals("THE POINT: the list read before the answer does not put it back in won", "lead", stage(model, "w"))
        assertEquals("a0V", model.uiState.value.objects.first { obj -> obj.id == "w" }.rank)
    }

    @Test
    fun `two quick moves of one card go one after the other, in the order made`() {
        val model = named()
        server.refuse("c1/-/objects", "Down", 500)
        val answering = CountDownLatch(1)
        server.on("c1/-/objects/w/move") {
            answering.await(10, TimeUnit.SECONDS)
            CrmServer.ok("{}")
        }
        model.moveObject("w", "stage", "lead", after = "a")
        model.moveObject("w", "stage", "", clear = true)
        settle()
        assertEquals("THE POINT: the second waits for the first's answer", 1, moves("w").size)
        answering.countDown()
        until("the second move") { moves("w").size == 2 }
        assertEquals("lead", moves("w")[0].get("value").asString)
        assertEquals("then the column for no value", "true", moves("w")[1].get("clear").asString)
        settle()
        assertEquals("", stage(model, "w"))
    }

    @Test
    fun `a move waiting beside a card whose own move fails goes beside its other neighbour`() {
        val model = named()
        server.refuse("c1/-/objects", "Down", 500)
        val answering = CountDownLatch(1)
        server.on("c1/-/objects/w/move") {
            answering.await(10, TimeUnit.SECONDS)
            MockResponse().setResponseCode(500).setBody("""{"error": "Refused"}""")
        }
        model.moveObject("w", "stage", "lead", after = "a")
        // Umbra dropped between Wren, drawn after Acme, and Bolt.
        model.moveObject("u", "stage", "lead", after = "w", before = "b")
        assertEquals(listOf("c1", "a", "w", "u", "b", "d", "x"), lead(model))
        answering.countDown()
        until("the second move") { moves("u").isNotEmpty() }
        assertFalse("THE POINT: not beside Wren, which is back in won", moves("u").single().has("after"))
        assertEquals("b", moves("u").single().get("before").asString)
        until("the failure") { failures.isNotEmpty() }
        settle()
        assertEquals("won", stage(model, "w"))
        assertEquals("and drawn where it goes, above Bolt", listOf("c1", "a", "u", "b", "d", "x"), lead(model))
    }

    @Test
    fun `a move still waiting stays where it was dropped through a reload`() {
        val model = named()
        val answering = CountDownLatch(1)
        server.on("c1/-/objects/w/move") {
            answering.await(10, TimeUnit.SECONDS)
            CrmServer.ok("{}")
        }
        model.moveObject("w", "stage", "lead", after = "a")
        model.refresh()
        until("the reload") { !model.uiState.value.isRefreshing && server.of("c1/-/objects").size >= 2 }
        settle()
        assertEquals("THE POINT: the reload, which does not hold the move yet, is drawn under it", "lead", stage(model, "w"))
        assertEquals(listOf("c1", "a", "w", "b", "d", "x"), lead(model))
        answering.countDown()
    }

    @Test
    fun `a row dropped on another takes the key the server gives it there`() {
        val model = board()
        server.refuse("c1/-/objects", "Down", 500)
        server.data("c1/-/objects/c1/update", """{"success": true, "parent": "b", "ranks": [{"id": "c1", "rank": "a9", "updated": 77}], "updated": 77}""")
        val target = TreeNode(model.uiState.value.objects.first { it.id == "b" }, depth = 0, hasChildren = false, isExpanded = true)
        handleTreeDrop(sourceId = "c1", targetNode = target, edge = DragEdge.On, viewModel = model)
        until("the key") { model.uiState.value.objects.first { obj -> obj.id == "c1" }.rank == "a9" }
        val contact = model.uiState.value.objects.first { obj -> obj.id == "c1" }
        assertEquals("THE POINT: under b, after its children, though the list cannot be read again", "b", contact.parent)
        assertEquals(77L, contact.updated)
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
