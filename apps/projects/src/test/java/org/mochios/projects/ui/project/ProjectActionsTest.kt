// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.project

import android.content.Intent
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.lifecycle.SavedStateHandle
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.android.share.EXTRA_ADDRESS
import org.mochios.android.share.EXTRA_WORDS
import org.mochios.android.share.ShareSheet
import org.mochios.android.ui.components.dnd.DragEdge
import org.mochios.android.ui.components.dnd.DragState
import org.mochios.android.ui.components.dnd.DropOrientation
import org.mochios.android.ui.components.dnd.dropTarget
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.mochios.projects.R
import org.mochios.projects.api.ProjectsApi
import org.mochios.projects.lib.ActiveViewStore
import org.mochios.projects.lib.CollapsedColumnsStore
import org.mochios.projects.repository.ProjectsRepository
import org.mochios.projects.ui.board.BoardView
import org.mochios.projects.ui.board.MoveObjectSheet
import org.mochios.projects.ui.board.rememberCardDragState
import org.mochios.projects.ui.projectlist.ProjectListViewModel
import org.mochios.projects.ui.tree.TreeNode
import org.mochios.projects.ui.tree.TreeView
import org.mochios.projects.ui.tree.handleTreeDrop
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.mochios.android.R as MochiR

/**
 * What the board and the list do with the project: a drop lands where the
 * server ranks it, a column or an object is deleted only after the web's
 * question, and every refusal once the project has loaded is shown.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class ProjectActionsTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var session: SessionManager
    private lateinit var repository: ProjectsRepository

    /** The project's access level, as the information answers it. */
    @Volatile private var access = "owner"

    /** The status options of a task, as `{"id", "name"}` objects. */
    @Volatile private var options = """[{"id": "todo", "name": "To do", "rank": 1}, {"id": "doing", "name": "Doing", "rank": 2}, {"id": "done", "name": "Done", "rank": 3}]"""

    /** The project's objects. */
    @Volatile private var objects = ""

    /** Whether the server refuses everything but the socket. */
    @Volatile private var refuse = false

    /** What a move answers, and what holds the answer back until released. */
    @Volatile private var answer = "{}"
    @Volatile private var answering: CountDownLatch? = null

    /** Whether the objects are listed; off, the listing fails, so a reload changes nothing. */
    @Volatile private var listing = true

    private val asked = ConcurrentLinkedQueue<Pair<String, String>>()
    private val failures = mutableListOf<MochiError>()
    private val jobs = mutableListOf<Job>()

    private fun item(id: String, cls: String, status: String, rank: String, title: String, parent: String = "", number: Int = 1, size: String = ""): String {
        val lane = if (size.isEmpty()) "" else """, "size": "$size""""
        return """{"id": "$id", "project": "p1", "class": "$cls", "parent": "$parent", "rank": "$rank", "number": $number,
            "values": {"title": "$title", "status": "$status"$lane}}"""
    }

    @Before
    fun begin() {
        AppContext.set(context)
        objects = listOf(
            item("a", "task", "todo", "a0", "Alpha", number = 1),
            item("c", "bug", "todo", "a1", "Child", parent = "a", number = 2),
            item("b", "task", "todo", "a2", "Beta", number = 3),
            item("h", "bug", "doing", "a3", "Hidden", number = 4),
            item("g", "task", "doing", "a4", "Gamma", number = 5),
        ).joinToString(",")
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                if (path == "/_/websocket") {
                    return MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            webSocket.close(code, reason)
                        }
                    })
                }
                val short = path.substringAfter("/projects/")
                asked.add(short to request.body.readUtf8())
                if (refuse) return MockResponse().setResponseCode(500).setBody("""{"error": "Refused"}""")
                if (short.endsWith("/move")) {
                    answering?.await(10, TimeUnit.SECONDS)
                    return MockResponse().setBody("""{"data": $answer}""")
                }
                if (short == "p1/-/objects" && !listing) return MockResponse().setResponseCode(500).setBody("""{"error": "Down"}""")
                val data = when (short) {
                    "p1/-/information" -> """{"project": {"id": "p1", "name": "Board", "prefix": "B", "access": "$access"},
                        "classes": [{"id": "task", "name": "Task", "title": "title", "rank": 1}, {"id": "bug", "name": "Bug", "title": "title", "rank": 2}],
                        "fields": {"task": [{"id": "title", "name": "Title", "fieldtype": "text", "rank": 1}, {"id": "status", "name": "Status", "fieldtype": "enumerated", "rank": 2},
                                {"id": "size", "name": "Size", "fieldtype": "enumerated", "rank": 3}],
                            "bug": [{"id": "title", "name": "Title", "fieldtype": "text", "rank": 1}, {"id": "status", "name": "Status", "fieldtype": "enumerated", "rank": 2}]},
                        "options": {"task": {"status": $options, "size": [{"id": "small", "name": "Small", "rank": 1}, {"id": "large", "name": "Large", "rank": 2}]},
                            "bug": {"status": $options}},
                        "views": [{"id": "v1", "name": "Board", "viewtype": "board", "columns": "status", "classes": ["task"], "filter": "status:todo"},
                            {"id": "v2", "name": "List", "viewtype": "list"},
                            {"id": "v3", "name": "Lanes", "viewtype": "board", "columns": "status", "rows": "size", "classes": ["task"]}],
                        "hierarchy": {"task": [""], "bug": ["", "task", "bug"]}}"""
                    "p1/-/objects" -> """{"objects": [$objects], "watched": []}"""
                    "p1/-/people" -> """{"people": []}"""
                    "p1/-/share" -> """{"link": "mochi://12D3KooWPeer/p1"}"""
                    "-/list" -> """{"projects": [{"id": "p1", "name": "Board"}]}"""
                    else -> "{}"
                }
                return MockResponse().setBody("""{"data": $data}""")
            }
        }
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        val api = Retrofit.Builder()
            .baseUrl(server.url("/projects/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ProjectsApi::class.java)
        session = SessionManager(context)
        runBlocking { session.setServerUrl(base) }
        socket = MochiWebSocket(OkHttpClient(), Gson(), object : SocketSession {
            override suspend fun serverUrl(): String = base

            override suspend fun token(app: String): String? = "token"

            override suspend fun invalidate(app: String) {
            }
        })
        repository = ProjectsRepository(api, FileStore(context), context, SessionManager(context))
    }

    @After
    fun end() {
        jobs.forEach { it.cancel() }
        socket.disconnectAll()
        server.shutdown()
    }

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    /** Waits a while for [done], and says whether it came. */
    private fun soon(done: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + 2_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            if (System.currentTimeMillis() > deadline) return false
            Thread.sleep(10)
        }
        return true
    }

    private fun settle() {
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
    }

    /** The project, loaded, on [view], with its failures collected. */
    private fun project(view: String = "v1"): ProjectViewModel {
        val model = ProjectViewModel(
            SavedStateHandle(mapOf("projectId" to "p1")),
            repository,
            socket,
            session,
            ActiveViewStore(context),
            CollapsedColumnsStore(context),
        )
        until { model.uiState.value.projectDetails != null && !model.uiState.value.isLoading }
        model.setActiveView(view)
        jobs += CoroutineScope(Dispatchers.Main).launch { model.actionFailed.collect { failures += it } }
        settle()
        return model
    }

    private fun sent(path: String): JsonObject =
        JsonParser.parseString(asked.last { (asked, _) -> asked == path }.second).asJsonObject

    // ---- Drops ----

    @Test
    fun `a column holds every object with its value, as the server ranks them`() {
        val model = project()
        assertEquals(
            "THE POINT: the child the board does not show is among them",
            listOf("a", "c", "b"),
            model.rankedColumn("status", "todo", "h"),
        )
    }

    @Test
    fun `a sort on the board does not change a column's rank order`() {
        val model = project()
        model.setSortField("number")
        model.setSortDirection("desc")
        assertEquals(listOf("a", "c", "b"), model.rankedColumn("status", "todo", "h"))
    }

    @Test
    fun `a drop with no column value is placed among the moved card's own column`() {
        val model = project()
        assertEquals(listOf("c", "b"), model.rankedColumn("status", "", "a"))
    }

    @Test
    fun `a drop at the end of a column lands after every object it holds`() {
        // Zed, a bug the task board does not show, is the last object To do holds.
        objects = "$objects," + item("z", "bug", "todo", "a9", "Zed", number = 6)
        val model = project()
        model.moveToEnd("h", "status", "todo")
        until { asked.any { (path, _) -> path == "p1/-/objects/h/move" } }
        val move = sent("p1/-/objects/h/move")
        assertEquals("THE POINT: after Zed, past Beta, the last card the board shows", "z", move["after"]?.asString)
        assertEquals("todo", move["value"]?.asString)
    }

    @Test
    fun `a move to the top of another column goes above every object it holds`() {
        // Yew, a bug the task board does not show, is the first object To do holds.
        objects = item("y", "bug", "todo", "Z0", "Yew", number = 6) + ",$objects"
        val model = project()
        model.moveToTop("h", "status", "todo")
        until { asked.any { (path, _) -> path == "p1/-/objects/h/move" } }
        val move = sent("p1/-/objects/h/move")
        assertEquals("THE POINT: above Yew, before Alpha, the first card the board shows", "y", move["before"]?.asString)
        assertFalse(move.has("after"))
    }

    /** The ids To do holds, in the order the board draws them. */
    private fun todo(model: ProjectViewModel): List<String> =
        model.sortObjects(model.uiState.value.objects.filter { obj -> obj.stringValue("status") == "todo" }).map { obj -> obj.id }

    @Test
    fun `a moved card is drawn beside its anchor at once and takes the keys the server wrote`() {
        val model = project()
        answering = CountDownLatch(1)
        answer = """{"success": true, "ranks": [{"id": "h", "rank": "a0V", "updated": 99}], "updated": 99}"""
        // A reload changes nothing, so only the answer can give the key.
        listing = false
        model.moveObject("h", "status", "todo", after = "a")
        assertEquals("THE POINT: after Alpha before the server answers", listOf("a", "h", "c", "b"), todo(model))
        answering?.countDown()
        until { model.uiState.value.objects.first { obj -> obj.id == "h" }.rank == "a0V" }
        assertEquals(listOf("a", "h", "c", "b"), todo(model))
        assertEquals(99L, model.uiState.value.objects.first { obj -> obj.id == "h" }.updated)
    }

    @Test
    fun `a move that fails puts the card back, though the reload fails too`() {
        val model = project()
        refuse = true
        model.moveObject("h", "status", "todo", after = "a")
        assertEquals("drawn where it was dropped", "todo", model.uiState.value.objects.first { obj -> obj.id == "h" }.stringValue("status"))
        until { failures.isNotEmpty() }
        val back = model.uiState.value.objects.first { obj -> obj.id == "h" }
        assertEquals("THE POINT: in its own column again", "doing", back.stringValue("status"))
        assertEquals("a3", back.rank)
    }

    @Test
    fun `under a descending manual sort a card dropped below another goes before it`() {
        val model = project()
        listing = false
        model.setSortDirection("desc")
        model.moveBeside("g", target = "a", below = true, field = "status", value = "todo")
        until { asked.any { (path, _) -> path == "p1/-/objects/g/move" } }
        val move = sent("p1/-/objects/g/move")
        assertEquals("THE POINT: keys drawn upside down put below before", "a", move["before"]?.asString)
        assertFalse(move.has("after"))
        assertEquals("drawn below Alpha at once", listOf("b", "c", "a", "g"), todo(model))
    }

    @Test
    fun `under a descending manual sort the end of a column is before its lowest key`() {
        val model = project()
        model.setSortDirection("desc")
        model.moveToEnd("h", "status", "todo")
        until { asked.any { (path, _) -> path == "p1/-/objects/h/move" } }
        val end = sent("p1/-/objects/h/move")
        assertEquals("THE POINT: the end drawn is the bottom, below Alpha", "a", end["before"]?.asString)
        assertFalse(end.has("after"))
    }

    @Test
    fun `under a sort by a field a drop names no neighbour, and one that changes nothing writes nothing`() {
        val model = project()
        model.setSortField("number")
        model.moveBeside("g", target = "a", below = true, field = "status", value = "todo")
        until { asked.any { (path, _) -> path == "p1/-/objects/g/move" } }
        val move = sent("p1/-/objects/g/move")
        assertFalse("THE POINT: the board shows no place, so none is named", move.has("after") || move.has("before"))
        assertEquals("todo", move["value"]?.asString)
        model.moveBeside("b", target = "a", below = true, field = "status", value = "todo")
        model.moveToEnd("a", "status", "todo")
        settle()
        assertTrue(asked.none { (path, _) -> path == "p1/-/objects/b/move" || path == "p1/-/objects/a/move" })
    }

    @Test
    fun `under a descending manual sort the top of a column is after its highest key`() {
        // Zed, a bug the task board does not show, holds To do's highest key.
        objects = "$objects," + item("z", "bug", "todo", "a9", "Zed", number = 6)
        val model = project()
        model.setSortDirection("desc")
        model.moveToTop("g", "status", "todo")
        until { asked.any { (path, _) -> path == "p1/-/objects/g/move" } }
        val top = sent("p1/-/objects/g/move")
        assertEquals("THE POINT: the top drawn is past Zed, the highest key", "z", top["after"]?.asString)
        assertFalse(top.has("before"))
    }

    // ---- The column for no value ----

    /** The board with Unset, which has no status, and Stale, whose status no column is drawn for. */
    private fun unset(): ProjectViewModel {
        objects = "$objects," + item("u", "task", "", "a5", "Unset", number = 6) + "," +
            item("s", "task", "gone", "a6", "Stale", number = 7)
        val model = project()
        // A reload changes nothing, so the list shows what the move drew.
        listing = false
        return model
    }

    private fun status(model: ProjectViewModel, id: String): String =
        model.uiState.value.objects.first { obj -> obj.id == id }.stringValue("status")

    @Test
    fun `a card dropped on the column for no value from another clears its column`() {
        val model = unset()
        model.moveBeside("g", target = "u", below = true, field = "status", value = "")
        assertEquals("THE POINT: drawn in the column for no value at once", "", status(model, "g"))
        until { asked.any { (path, _) -> path == "p1/-/objects/g/move" } }
        val move = sent("p1/-/objects/g/move")
        assertEquals("THE POINT: the move clears the column, which an empty value alone keeps", "true", move["clear"]?.asString)
        assertEquals("status", move["field"]?.asString)
        assertEquals("", move["value"]?.asString)
        assertEquals("u", move["after"]?.asString)
    }

    @Test
    fun `a card moved among those drawn in the column for no value keeps its value`() {
        val model = unset()
        model.moveBeside("s", target = "u", below = false, field = "status", value = "")
        until { asked.any { (path, _) -> path == "p1/-/objects/s/move" } }
        assertFalse("THE POINT: a value no column is drawn for is not cleared by a reorder", sent("p1/-/objects/s/move").has("clear"))
        assertEquals("gone", status(model, "s"))
        model.moveBeside("u", target = "s", below = true, field = "status", value = "")
        until { asked.any { (path, _) -> path == "p1/-/objects/u/move" } }
        assertFalse("nor one with no value", sent("p1/-/objects/u/move").has("clear"))
    }

    @Test
    fun `a drop at the end of the column for no value goes past every card drawn there`() {
        val model = unset()
        model.moveToEnd("g", "status", "")
        until { asked.any { (path, _) -> path == "p1/-/objects/g/move" } }
        val end = sent("p1/-/objects/g/move")
        assertEquals("THE POINT: after Stale, which the column draws though it holds a value", "s", end["after"]?.asString)
        assertEquals("true", end["clear"]?.asString)
    }

    @Test
    fun `under a sort by a field a drop on the column for no value still clears the column`() {
        val model = unset()
        model.setSortField("number")
        model.moveBeside("g", target = "u", below = true, field = "status", value = "")
        until { asked.any { (path, _) -> path == "p1/-/objects/g/move" } }
        val move = sent("p1/-/objects/g/move")
        assertEquals("THE POINT: the column changes, so the move goes", "true", move["clear"]?.asString)
        assertFalse("with no place, as the board draws none", move.has("after") || move.has("before"))
    }

    @Test
    fun `the Move sheet places a card whose value no column is drawn for among the cards it is drawn with`() {
        val model = unset()
        val stale = model.uiState.value.objects.first { obj -> obj.id == "s" }
        rule.setContent { MoveObjectSheet(stale, model, onDismiss = {}) }
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(R.string.projects_move_position_top)).performSemanticsAction(SemanticsActions.OnClick)
        until { asked.any { (path, _) -> path == "p1/-/objects/s/move" } }
        val top = sent("p1/-/objects/s/move")
        assertEquals("THE POINT: no option the server would refuse, but the column it is drawn in", "", top["value"]?.asString)
        assertFalse("which it keeps", top.has("clear"))
        assertEquals("above Unset, the first card there", "u", top["before"]?.asString)
    }

    @Test
    fun `a card dropped on another on a board without lanes names the column field`() {
        val model = project()
        listing = false
        board(model)
        val beta = rule.onNodeWithText("Beta").fetchSemanticsNode().boundsInRoot
        rule.onRoot().performTouchInput {
            down(beta.center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(Offset(0f, 4f))
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        val alpha = rule.onNodeWithText("Alpha").fetchSemanticsNode().boundsInRoot
        rule.onRoot().performTouchInput {
            moveTo(Offset(alpha.center.x, alpha.top + 1f))
            up()
        }
        until { asked.any { (path, _) -> path == "p1/-/objects/b/move" } }
        val move = sent("p1/-/objects/b/move")
        assertEquals("THE POINT: the column's field, which a value needs, or the server refuses the move", "status", move["field"]?.asString)
        assertEquals("todo", move["value"]?.asString)
    }

    // ---- The Move sheet ----

    @Test
    fun `the Move sheet offers the cards beside it in the order the board draws them`() {
        // Epsilon is a third card the task board draws in To do; Zed is a bug it does not.
        objects = "$objects," + item("e", "task", "todo", "a5", "Epsilon", number = 6) + "," +
            item("z", "bug", "todo", "a9", "Zed", number = 7)
        val model = project()
        assertEquals("the child and the bug the board does not show are left out", listOf("a", "e"), model.movePlaces("b").map { obj -> obj.id })
        model.setSortDirection("desc")
        assertEquals("THE POINT: upside down, as the board draws them", listOf("e", "a"), model.movePlaces("b").map { obj -> obj.id })
        model.setSortField("number")
        assertEquals("a sort by a field draws no place to go", emptyList<String>(), model.movePlaces("b").map { obj -> obj.id })
    }

    @Test
    fun `the Move sheet offers the cards of its own lane, and a card in none sits with the others in none`() {
        objects = listOf(
            item("a", "task", "todo", "a0", "Alpha", size = "small"),
            item("b", "task", "todo", "a1", "Beta", size = "large"),
            item("e", "task", "todo", "a2", "Epsilon", size = "small"),
            item("u", "task", "todo", "a3", "Unsized"),
            item("v", "task", "todo", "a4", "Vast", size = "huge"),
            item("n", "task", "", "a5", "Nowhere"),
            item("s", "task", "gone", "a6", "Stale"),
        ).joinToString(",")
        val model = project("v3")
        assertEquals("THE POINT: Beta is drawn in another lane", listOf("e"), model.movePlaces("a").map { obj -> obj.id })
        assertEquals("a size no lane is drawn for is drawn with none", listOf("v"), model.movePlaces("u").map { obj -> obj.id })
        assertEquals("a status no column is drawn for is drawn with none", listOf("s"), model.movePlaces("n").map { obj -> obj.id })
    }

    /** The Move sheet open on Beta under a descending manual sort, its place [text] chosen; answers the move sent. */
    private fun descendingSheet(text: String): JsonObject {
        objects = "$objects," + item("e", "task", "todo", "a5", "Epsilon", number = 6) + "," +
            item("z", "bug", "todo", "a9", "Zed", number = 7)
        val model = project()
        listing = false
        model.setSortDirection("desc")
        val beta = model.uiState.value.objects.first { obj -> obj.id == "b" }
        rule.setContent { MoveObjectSheet(beta, model, onDismiss = {}) }
        rule.waitForIdle()
        rule.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick)
        until { asked.any { (path, _) -> path == "p1/-/objects/b/move" } }
        return sent("p1/-/objects/b/move")
    }

    @Test
    fun `under a descending manual sort the Move sheet's After goes below the card as the board draws it`() {
        val after = descendingSheet(context.getString(R.string.projects_move_after, "Alpha"))
        assertEquals("THE POINT: drawn below Alpha is before it", "a", after["before"]?.asString)
        assertFalse(after.has("after"))
    }

    @Test
    fun `under a descending manual sort the Move sheet's Top goes above every card as the board draws them`() {
        val top = descendingSheet(context.getString(R.string.projects_move_position_top))
        assertEquals("THE POINT: the top drawn is past Zed, the highest key", "z", top["after"]?.asString)
        assertFalse(top.has("before"))
    }

    @Test
    fun `the tree places by an edge only under a manual sort, upside down when it descends`() {
        val model = project("v2")
        val objects = model.uiState.value.objects
        val alpha = TreeNode(objects.first { obj -> obj.id == "a" }, 0, hasChildren = true, isExpanded = true)
        val beta = TreeNode(objects.first { obj -> obj.id == "b" }, 0, hasChildren = false, isExpanded = false)
        model.setSortField("number")
        handleTreeDrop("b", alpha, DragEdge.Top, model)
        handleTreeDrop("c", beta, DragEdge.Bottom, model)
        settle()
        assertTrue("THE POINT: a sort by number draws no place beside a row", asked.none { (path, _) -> path.endsWith("/move") })
        model.setSortField("rank")
        model.setSortDirection("desc")
        handleTreeDrop("b", alpha, DragEdge.Bottom, model)
        until { asked.any { (path, _) -> path == "p1/-/objects/b/move" } }
        assertEquals("below Alpha upside down is before it", "a", sent("p1/-/objects/b/move")["before"]?.asString)
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
    fun `top-level rows reorder by drag`() {
        val model = project("v2")
        val alpha = model.uiState.value.objects.first { obj -> obj.id == "a" }
        handleTreeDrop("b", TreeNode(alpha, 0, hasChildren = true, isExpanded = true), DragEdge.Top, model)
        until { asked.any { (path, _) -> path == "p1/-/objects/b/move" } }
        val move = sent("p1/-/objects/b/move")
        assertEquals("THE POINT: above the row it was dropped on", "a", move["before"]?.asString)
        assertFalse("it stays at the top level, so no parent goes", move.has("parent"))
    }

    @Test
    fun `a row dropped beside a row of another parent moves there in one move`() {
        val model = project("v2")
        val beta = model.uiState.value.objects.first { obj -> obj.id == "b" }
        handleTreeDrop("c", TreeNode(beta, 0, hasChildren = false, isExpanded = false), DragEdge.Bottom, model)
        until { asked.any { (path, _) -> path == "p1/-/objects/c/move" } }
        val move = sent("p1/-/objects/c/move")
        assertEquals("THE POINT: the new parent and the place beside Beta together", "", move["parent"]?.asString)
        assertEquals("b", move["after"]?.asString)
    }

    @Test
    fun `moving a row to the top level sends an empty parent`() {
        val model = project("v2")
        model.reparentObject("c", "")
        until { asked.any { (path, _) -> path == "p1/-/objects/c/update" } }
        assertEquals("", sent("p1/-/objects/c/update")["parent"]?.asString)
    }

    // ---- Filters ----

    @Test
    fun `a view's stored filter is not applied`() {
        val model = project()
        assertEquals(listOf("a", "b", "g"), model.getFilteredObjects().map { obj -> obj.id }.sorted())
    }

    // ---- Failures ----

    @Test
    fun `every refusal once the project has loaded is shown`() {
        val model = project()
        refuse = true
        val actions = listOf<() -> Unit>(
            { model.deleteObject("a") },
            { model.moveObject("a", "status", "doing", after = "g") },
            { model.reparentObject("c", "") },
            { model.renameColumnOption("status", "todo", "Waiting") },
            { model.deleteColumnOption("status", "done") },
            { model.reorderColumnOptions("status", listOf("done", "doing", "todo")) },
            { model.exportProject() },
            { model.refresh() },
        )
        actions.forEachIndexed { index, action ->
            action()
            soon { failures.size > index }
        }
        until { !model.uiState.value.isExporting && !model.uiState.value.isRefreshing }
        settle()
        assertEquals("THE POINT: each of the eight is reported", 8, failures.size)
        assertNull("nothing goes to the error only an empty page draws", model.uiState.value.error)
        assertNotNull(model.uiState.value.projectDetails)
    }

    /**
     * What the system share sheet opened for, once it opens; the launch of
     * the test's own host activity is passed over.
     */
    private fun sheet(): Intent {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
            val started = shadowOf(context).nextStartedActivity
            if (started == null) {
                Thread.sleep(10)
            } else if (started.action == Intent.ACTION_CHOOSER) {
                return IntentCompat.getParcelableExtra(started, Intent.EXTRA_INTENT, Intent::class.java)!!
            }
        }
        throw AssertionError("the share sheet did not open")
    }

    @Test
    fun `the project's Share hands other apps the link it is joined by, and Mochi the project by name and address`() {
        val model = project()
        rule.setContent { ShareSheet(model.shares) }
        rule.runOnIdle { model.share() }
        val send = sheet()
        assertTrue(asked.any { (path, _) -> path == "p1/-/share" })
        assertEquals("mochi://12D3KooWPeer/p1", send.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("Board", send.getStringExtra(EXTRA_WORDS))
        assertEquals(server.url("/").toString().trimEnd('/') + "/projects/p1", send.getStringExtra(EXTRA_ADDRESS))
    }

    @Test
    fun `a failed unsubscribe on the list of projects is shown`() {
        val model = ProjectListViewModel(repository)
        until { model.uiState.value.projects.isNotEmpty() }
        jobs += CoroutineScope(Dispatchers.Main).launch { model.actionFailed.collect { failures += it } }
        settle()
        refuse = true
        model.unsubscribe("p1")
        until { failures.isNotEmpty() }
        assertEquals("the row stays", listOf("p1"), model.uiState.value.projects.map { project -> project.id })
        assertNull(model.uiState.value.error)
    }

    // ---- Column delete ----

    private fun board(model: ProjectViewModel) {
        rule.setContent {
            BoardView(
                objects = model.uiState.value.objects,
                view = model.getActiveView(),
                viewModel = model,
                onObjectClick = {},
            )
        }
        rule.waitForIdle()
    }

    private fun menu() {
        rule.onAllNodesWithContentDescription(context.getString(MochiR.string.common_more_options))[0].performClick()
        rule.waitForIdle()
    }

    private fun offered(): Boolean =
        rule.onAllNodesWithText(context.getString(MochiR.string.common_delete)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `a column holding only objects the board does not show offers no delete`() {
        options = """[{"id": "doing", "name": "Doing", "rank": 1}]"""
        objects = item("h", "bug", "doing", "a3", "Hidden")
        board(project())
        menu()
        assertTrue("THE POINT: its hidden object would keep a deleted option", !offered())
    }

    @Test
    fun `an empty column is deleted only after the web's question`() {
        options = """[{"id": "done", "name": "Done", "rank": 1}]"""
        objects = ""
        board(project())
        menu()
        rule.onNodeWithText(context.getString(MochiR.string.common_delete)).performClick()
        rule.waitForIdle()
        assertTrue(
            "THE POINT: nothing is deleted before the answer",
            asked.none { (path, _) -> path.endsWith("/options/done/delete") },
        )
        rule.onNodeWithText(context.getString(R.string.projects_board_delete_column_message, "Done")).assertExists()
        rule.onNodeWithText(context.getString(MochiR.string.common_delete)).performClick()
        until { asked.any { (path, _) -> path == "p1/-/classes/task/fields/status/options/done/delete" } }
    }

    @Test
    fun `a column that holds an object by the answer is not deleted`() {
        val model = project()
        model.deleteColumnOption("status", "doing")
        settle()
        assertTrue(
            "THE POINT: its objects would keep an option that no longer exists",
            asked.none { (path, _) -> path.endsWith("/options/doing/delete") },
        )
    }

    @Test
    fun `a column is offered for deletion only to a designer`() {
        access = "write"
        options = """[{"id": "done", "name": "Done", "rank": 1}]"""
        objects = ""
        board(project())
        menu()
        assertTrue(!offered())
    }

    // ---- Object delete ----

    private fun tree(model: ProjectViewModel) {
        rule.setContent {
            TreeView(
                objects = model.getFilteredObjects(),
                view = model.getActiveView(),
                viewModel = model,
                onObjectClick = {},
            )
        }
        rule.waitForIdle()
    }

    @Test
    fun `a row is deleted only after a question naming it and what goes with it`() {
        objects = listOf(
            item("a", "task", "todo", "a0", "Alpha"),
            item("c", "bug", "todo", "a1", "Child", parent = "a"),
            item("d", "bug", "todo", "a2", "Deep", parent = "c"),
        ).joinToString(",")
        tree(project("v2"))
        menu()
        rule.onNodeWithText(context.getString(MochiR.string.common_delete)).performClick()
        rule.waitForIdle()
        assertTrue(asked.none { (path, _) -> path.endsWith("/delete") })
        val question = context.resources.getQuantityString(R.plurals.projects_object_delete_descendants, 2, "Alpha", "2")
        rule.onNodeWithText(question).assertExists()
        rule.onNodeWithText(context.getString(MochiR.string.common_delete)).performClick()
        until { asked.any { (path, _) -> path == "p1/-/objects/a/delete" } }
    }

    @Test
    fun `a row with nothing under it is named alone`() {
        objects = item("a", "task", "todo", "a0", "Alpha")
        tree(project("v2"))
        menu()
        rule.onNodeWithText(context.getString(MochiR.string.common_delete)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(R.string.projects_object_delete_named, "Alpha")).assertExists()
    }
}
