// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.project

import android.content.Intent
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
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

    /** The size options of a task. */
    @Volatile private var sizes = """[{"id": "small", "name": "Small", "rank": 1}, {"id": "large", "name": "Large", "rank": 2}]"""

    /** The project's objects. */
    @Volatile private var objects = ""

    /** Whether the server refuses everything but the socket. */
    @Volatile private var refuse = false

    /** What a move answers, and what holds the answer back until released. */
    @Volatile private var answer = "{}"
    @Volatile private var answering: CountDownLatch? = null

    /** The moves refused once released, by path; and what an object update answers. */
    @Volatile private var refused = emptySet<String>()
    @Volatile private var updated = "{}"

    /** Whether the objects are listed; off, the listing fails, so a reload changes nothing. */
    @Volatile private var listing = true

    /**
     * Holds the next listing of the objects until released, which then
     * answers them as they were when it was asked for.
     */
    @Volatile private var reading: CountDownLatch? = null

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
                    if (short in refused) return MockResponse().setResponseCode(500).setBody("""{"error": "Refused"}""")
                    return MockResponse().setBody("""{"data": $answer}""")
                }
                if (short.endsWith("/update")) return MockResponse().setBody("""{"data": $updated}""")
                val held = reading
                if (short == "p1/-/objects" && held != null) {
                    reading = null
                    val then = objects
                    held.await(10, TimeUnit.SECONDS)
                    return MockResponse().setBody("""{"data": {"objects": [$then], "watched": []}}""")
                }
                if (short == "p1/-/objects" && !listing) return MockResponse().setResponseCode(500).setBody("""{"error": "Down"}""")
                val data = when (short) {
                    "p1/-/information" -> """{"project": {"id": "p1", "name": "Board", "prefix": "B", "access": "$access"},
                        "classes": [{"id": "task", "name": "Task", "title": "title", "rank": 1}, {"id": "bug", "name": "Bug", "title": "title", "rank": 2}],
                        "fields": {"task": [{"id": "title", "name": "Title", "fieldtype": "text", "rank": 1}, {"id": "status", "name": "Status", "fieldtype": "enumerated", "rank": 2},
                                {"id": "size", "name": "Size", "fieldtype": "enumerated", "rank": 3}],
                            "bug": [{"id": "title", "name": "Title", "fieldtype": "text", "rank": 1}, {"id": "status", "name": "Status", "fieldtype": "enumerated", "rank": 2}]},
                        "options": {"task": {"status": $options, "size": $sizes},
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
        // Held until the drop is seen drawn: a refusal answered at once can
        // resume the move before the call that made it returns.
        answering = CountDownLatch(1)
        refused = setOf("p1/-/objects/h/move")
        listing = false
        model.moveObject("h", "status", "todo", after = "a")
        assertEquals("drawn where it was dropped", "todo", model.uiState.value.objects.first { obj -> obj.id == "h" }.stringValue("status"))
        answering?.countDown()
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
        assertFalse(
            "THE POINT: the column it stays in is not named, so the server takes Unset, whose status is not Stale's, beside it",
            top.has("field") || top.has("value"),
        )
        assertFalse("and it keeps its value", top.has("clear"))
        assertEquals("above Unset, the first card there", "u", top["before"]?.asString)
    }

    @Test
    fun `a card reordered in the column for no value is drawn beside a card whose value is not its own`() {
        val model = unset()
        answering = CountDownLatch(1)
        // Unset dropped below Stale, which holds a status no column is drawn for.
        model.moveBeside("u", target = "s", below = true, field = "status", value = "")
        val none = model.sortObjects(model.uiState.value.objects.filter { obj -> obj.stringValue("status") !in setOf("todo", "doing", "done") })
        assertEquals("THE POINT: below Stale at once, as the server places it", listOf("s", "u"), none.map { obj -> obj.id })
        answering?.countDown()
    }

    @Test
    fun `a card of a class without the column field is reordered without naming it`() {
        // A note has no status field, so it is drawn in the column for no value.
        objects = "$objects," + """{"id": "n", "project": "p1", "class": "note", "parent": "", "rank": "a8", "number": 8, "values": {"title": "Note"}}"""
        val model = unset()
        model.moveBeside("n", target = "u", below = true, field = "status", value = "")
        until { asked.any { (path, _) -> path == "p1/-/objects/n/move" } }
        assertFalse("THE POINT: named, the server refuses the move, the field not found", sent("p1/-/objects/n/move").has("field"))
        assertEquals("u", sent("p1/-/objects/n/move")["after"]?.asString)
    }

    @Test
    fun `a card dropped on another in its own column names no column`() {
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
        assertFalse("THE POINT: the column does not change, so it is not named", move.has("field") || move.has("value"))
        assertEquals("above Alpha", "a", move["before"]?.asString)
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

    // ---- Both neighbours ----

    @Test
    fun `a card dropped between two others names both, as the board draws them`() {
        // Epsilon is a third card the task board draws in To do, below Beta.
        objects = "$objects," + item("e", "task", "todo", "a5", "Epsilon", number = 6)
        val model = project()
        listing = false
        board(model)
        val epsilon = rule.onNodeWithText("Epsilon").fetchSemanticsNode().boundsInRoot
        rule.onRoot().performTouchInput {
            down(epsilon.center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(Offset(0f, 4f))
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        val beta = rule.onNodeWithText("Beta").fetchSemanticsNode().boundsInRoot
        rule.onRoot().performTouchInput {
            moveTo(Offset(beta.center.x, beta.top + 1f))
            up()
        }
        until { asked.any { (path, _) -> path == "p1/-/objects/e/move" } }
        val move = sent("p1/-/objects/e/move")
        assertEquals("above Beta", "b", move["before"]?.asString)
        assertEquals("THE POINT: and below Alpha, the card drawn above the drop", "a", move["after"]?.asString)
    }

    @Test
    fun `under a descending manual sort tied cards are drawn in the server's order upside down, and a drop between them names both`() {
        // Xeno and Yew share a key; the server lists Xeno, the newer, first.
        objects = listOf(
            item("x", "task", "todo", "a0", "Xeno"),
            item("y", "task", "todo", "a0", "Yew"),
            item("d", "task", "doing", "a1", "Dash"),
        ).joinToString(",")
        val model = project()
        listing = false
        answering = CountDownLatch(1)
        model.setSortDirection("desc")
        assertEquals("THE POINT: the exact reverse of the server's order, ties too", listOf("y", "x"), todo(model))
        // Dropped below Yew as drawn, so above Xeno.
        model.moveBeside("d", target = "y", below = true, field = "status", value = "todo", other = "x")
        until { asked.any { (path, _) -> path == "p1/-/objects/d/move" } }
        val move = sent("p1/-/objects/d/move")
        assertEquals("below Yew upside down is before it", "y", move["before"]?.asString)
        assertEquals("THE POINT: and after Xeno, which the server unties below Yew", "x", move["after"]?.asString)
        assertEquals("drawn between them at once", listOf("y", "d", "x"), todo(model))
        answering?.countDown()
    }

    @Test
    fun `a move is drawn beside its other neighbour when the first has left the column`() {
        val model = project()
        listing = false
        answering = CountDownLatch(1)
        // Gamma, named after, is in Doing now; Beta, named before, is in To do.
        model.moveObject("h", "status", "todo", after = "g", before = "b")
        assertEquals("THE POINT: above Beta, as the server places it", listOf("a", "c", "h", "b"), todo(model))
        answering?.countDown()
        until { asked.any { (path, _) -> path == "p1/-/objects/h/move" } }
        val move = sent("p1/-/objects/h/move")
        assertEquals("g", move["after"]?.asString)
        assertEquals("b", move["before"]?.asString)
    }

    @Test
    fun `a tree row dropped between two siblings names both`() {
        val model = project("v2")
        val beta = model.uiState.value.objects.first { obj -> obj.id == "b" }
        handleTreeDrop("g", TreeNode(beta, 0, hasChildren = false, isExpanded = false), DragEdge.Top, model)
        until { asked.any { (path, _) -> path == "p1/-/objects/g/move" } }
        val move = sent("p1/-/objects/g/move")
        assertEquals("above Beta", "b", move["before"]?.asString)
        assertEquals("THE POINT: and below Alpha, the sibling drawn above it, not Child, Alpha's own row", "a", move["after"]?.asString)
    }

    // ---- The lane for none ----

    /** The board with lanes of size: Huge's size no lane is drawn for, so it is drawn in the lane for none. */
    private fun lanes(): ProjectViewModel {
        objects = listOf(
            item("a", "task", "todo", "a0", "Alpha", size = "small"),
            item("u", "task", "todo", "a1", "Unsized"),
            item("v", "task", "todo", "a2", "Huge", size = "huge"),
        ).joinToString(",")
        val model = project("v3")
        listing = false
        return model
    }

    @Test
    fun `a card reordered in the lane for none keeps a value no lane is drawn for`() {
        val model = lanes()
        answering = CountDownLatch(1)
        model.moveBeside("v", target = "u", below = false, field = "status", value = "todo", rowField = "size", rowValue = "")
        assertEquals("THE POINT: drawn above Unsized at once, as the server places it", listOf("a", "v", "u"), todo(model))
        answering?.countDown()
        until { asked.any { (path, _) -> path == "p1/-/objects/v/move" } }
        assertFalse(
            "THE POINT: it stays in the lane it is drawn in, so no lane is named: its own would refuse Unsized beside it, and \"\" would clear it",
            sent("p1/-/objects/v/move").has("row"),
        )
        assertEquals("u", sent("p1/-/objects/v/move")["before"]?.asString)
        assertEquals("huge", model.uiState.value.objects.first { obj -> obj.id == "v" }.stringValue("size"))
    }

    @Test
    fun `a card dropped in the lane for none from a drawn lane has its lane cleared`() {
        val model = lanes()
        model.moveBeside("a", target = "u", below = true, field = "status", value = "todo", rowField = "size", rowValue = "")
        until { asked.any { (path, _) -> path == "p1/-/objects/a/move" } }
        assertEquals("THE POINT: from Small into the lane for none", """{"field":"size","value":""}""", sent("p1/-/objects/a/move")["row"]?.asString)
    }

    @Test
    fun `a card dropped at the end of its own lane keeps its lane, and under a sort by a field writes nothing`() {
        val model = lanes()
        model.moveToEnd("a", "status", "todo", rowField = "size", rowValue = "small")
        until { asked.any { (path, _) -> path == "p1/-/objects/a/move" } }
        assertFalse("THE POINT: the lane it is drawn in, its own, is not named", sent("p1/-/objects/a/move").has("row"))
        model.setSortField("number")
        model.moveBeside("v", target = "u", below = true, field = "status", value = "todo", rowField = "size", rowValue = "")
        settle()
        assertTrue("nothing changes, so nothing goes", asked.none { (path, _) -> path == "p1/-/objects/v/move" })
    }

    @Test
    fun `a card dropped at the end of another lane goes after the last card of that lane`() {
        // Kilo, in Large, holds To do's last key but Lima's.
        objects = listOf(
            item("a", "task", "todo", "a0", "Alpha", size = "small"),
            item("e", "task", "todo", "a1", "Echo", size = "small"),
            item("k", "task", "todo", "a2", "Kilo", size = "large"),
            item("l", "task", "todo", "a3", "Lima", size = "large"),
        ).joinToString(",")
        val model = project("v3")
        listing = false
        model.moveToEnd("l", "status", "todo", rowField = "size", rowValue = "small")
        until { asked.any { (path, _) -> path == "p1/-/objects/l/move" } }
        val move = sent("p1/-/objects/l/move")
        assertEquals("""{"field":"size","value":"small"}""", move["row"]?.asString)
        assertEquals("THE POINT: after Echo, Small's last, not Kilo, which the server takes in no lane but Large", "e", move["after"]?.asString)
    }

    // ---- What a move goes among ----

    /** The board with lanes of size: Kilo, in Large, holds To do's last key. */
    private fun laned(): ProjectViewModel {
        objects = listOf(
            item("a", "task", "todo", "a0", "Alpha", size = "small"),
            item("e", "task", "todo", "a1", "Echo", size = "small"),
            item("k", "task", "todo", "a2", "Kilo", size = "large"),
        ).joinToString(",")
        val model = project("v3")
        listing = false
        return model
    }

    @Test
    fun `a drop at the end of its own column on a board with lanes names neither, and ends in the lane it stays in`() {
        val model = laned()
        // On the column's empty space, which names no lane.
        model.moveToEnd("a", "status", "todo")
        until { asked.any { (path, _) -> path == "p1/-/objects/a/move" } }
        val move = sent("p1/-/objects/a/move")
        assertFalse("THE POINT: the column does not change, so it is not named", move.has("field"))
        assertFalse("THE POINT: nor does the lane", move.has("row"))
        assertEquals("the end is Small's, after Echo, not Kilo", "e", move["after"]?.asString)
    }

    @Test
    fun `the Move sheet on a board with lanes names only what was chosen`() {
        val model = laned()
        val alpha = model.uiState.value.objects.first { obj -> obj.id == "a" }
        rule.setContent { MoveObjectSheet(alpha, model, onDismiss = {}) }
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(R.string.projects_move_after, "Echo")).performSemanticsAction(SemanticsActions.OnClick)
        until { moves("a").isNotEmpty() }
        val after = moves("a").single()
        assertFalse("THE POINT: a place in its own column does not name it", after.has("field"))
        assertFalse("THE POINT: nor its own lane", after.has("row"))
        assertEquals("e", after["after"]?.asString)
        rule.onNodeWithText("Large").performSemanticsAction(SemanticsActions.OnClick)
        until { moves("a").size == 2 }
        val lane = moves("a")[1]
        assertFalse("THE POINT: a lane chosen names no column", lane.has("field") || lane.has("value"))
        assertEquals("""{"field":"size","value":"large"}""", lane["row"]?.asString)
    }

    @Test
    fun `a tree row reordered among its siblings names their parent as its scope`() {
        // Kit is a second child of Alpha, after Child.
        objects = "$objects," + item("k", "bug", "todo", "a1V", "Kit", parent = "a", number = 6)
        val model = project("v2")
        val beta = model.uiState.value.objects.first { obj -> obj.id == "b" }
        handleTreeDrop("g", TreeNode(beta, 0, hasChildren = false, isExpanded = false), DragEdge.Top, model)
        until { moves("g").isNotEmpty() }
        assertEquals("THE POINT: the top level, as \"\"", "", moves("g").single()["scope"]?.asString)
        assertFalse("its parent stays, so none goes", moves("g").single().has("parent"))
        val child = model.uiState.value.objects.first { obj -> obj.id == "c" }
        handleTreeDrop("k", TreeNode(child, 1, hasChildren = false, isExpanded = false), DragEdge.Top, model)
        until { moves("k").isNotEmpty() }
        assertEquals("THE POINT: Alpha's children", "a", moves("k").single()["scope"]?.asString)
        assertEquals("above Child", "c", moves("k").single()["before"]?.asString)
        assertFalse(moves("k").single().has("parent"))
    }

    // ---- A tree whose parents a filter hides ----

    /**
     * The list with Epic filtered out: its stories, One and Two, are drawn at
     * the top level between Top and Last, a bug the hierarchy lets go under
     * Epic, beside Parent and its Kid. Answers the tree's rows in the order
     * drawn.
     */
    private fun hidden(): Pair<ProjectViewModel, List<org.mochios.projects.model.ProjectObject>> {
        objects = listOf(
            item("e", "task", "todo", "a0", "Epic"),
            item("s1", "bug", "todo", "a1", "One", parent = "e"),
            item("t1", "task", "todo", "a2", "Top"),
            item("s2", "bug", "todo", "a3", "Two", parent = "e"),
            item("t2", "bug", "todo", "a4", "Last"),
            item("p", "task", "todo", "a5", "Parent"),
            item("k", "bug", "todo", "a6", "Kid", parent = "p"),
        ).joinToString(",")
        val model = project("v2")
        listing = false
        return model to model.uiState.value.objects.filter { obj -> obj.id != "e" }
    }

    private fun node(model: ProjectViewModel, id: String) =
        TreeNode(model.uiState.value.objects.first { obj -> obj.id == id }, 0, hasChildren = false, isExpanded = true)

    @Test
    fun `a row dropped beside a row whose parent is hidden stays where it is in the tree, between the rows drawn there`() {
        val (model, drawn) = hidden()
        handleTreeDrop("t2", node(model, "s1"), DragEdge.Bottom, model, drawn)
        until { moves("t2").isNotEmpty() }
        val move = moves("t2").single()
        assertFalse("THE POINT: not put under Epic, which the tree does not draw", move.has("parent"))
        assertEquals("after One", "s1", move["after"]?.asString)
        assertEquals("THE POINT: and before Top, the row drawn below One, not Two, Epic's next", "t1", move["before"]?.asString)
        assertFalse("THE POINT: the top level draws rows of two parents, so none is named", move.has("scope"))
    }

    @Test
    fun `a row whose parent is hidden dropped beside a top-level row keeps its parent`() {
        val (model, drawn) = hidden()
        handleTreeDrop("s2", node(model, "t1"), DragEdge.Top, model, drawn)
        until { moves("s2").isNotEmpty() }
        val move = moves("s2").single()
        assertFalse("THE POINT: it is drawn at the top level already, so it stays under Epic", move.has("parent"))
        assertFalse("THE POINT: and the top level, of two parents, names none", move.has("scope"))
        assertEquals("t1", move["before"]?.asString)
        assertEquals("One, drawn above Top", "s1", move["after"]?.asString)
    }

    @Test
    fun `a row reordered after the last child of a hidden parent goes before the row drawn below, not to the end`() {
        val (model, drawn) = hidden()
        handleTreeDrop("s1", node(model, "s2"), DragEdge.Bottom, model, drawn)
        until { moves("s1").isNotEmpty() }
        val move = moves("s1").single()
        assertFalse("THE POINT: named, Epic's last child would be the scope's end, and One would jump past every key", move.has("scope"))
        assertEquals("s2", move["after"]?.asString)
        assertEquals("Last, drawn below Two", "t2", move["before"]?.asString)
    }

    @Test
    fun `a row under a drawn parent dropped beside a row whose parent is hidden is refused`() {
        val (model, drawn) = hidden()
        handleTreeDrop("k", node(model, "s1"), DragEdge.Top, model, drawn)
        settle()
        assertTrue("THE POINT: it would go under Epic, out of sight", moves("k").isEmpty())
    }

    @Test
    fun `a row moved into a parent's children takes its board values at once, with its own children`() {
        objects = listOf(
            item("p", "task", "doing", "a0", "Parent", size = "large"),
            item("q", "bug", "doing", "a1", "Kin", parent = "p"),
            item("x", "bug", "todo", "a2", "Mover"),
            item("y", "bug", "todo", "a3", "Under", parent = "x"),
        ).joinToString(",")
        val model = project("v2")
        listing = false
        answering = CountDownLatch(1)
        handleTreeDrop("x", TreeNode(model.uiState.value.objects.first { obj -> obj.id == "q" }, 1, hasChildren = false, isExpanded = true), DragEdge.Bottom, model)
        val drawn = model.uiState.value.objects.associateBy { obj -> obj.id }
        assertEquals("p", drawn.getValue("x").parent)
        assertEquals("THE POINT: Parent's status, before the server answers", "doing", drawn.getValue("x").stringValue("status"))
        assertEquals("THE POINT: and its child's", "doing", drawn.getValue("y").stringValue("status"))
        assertEquals("a bug has no size, so it takes none", "", drawn.getValue("x").stringValue("size"))
        answering?.countDown()
        until { moves("x").isNotEmpty() }
        assertEquals("p", moves("x").single()["parent"]?.asString)
    }

    @Test
    fun `a row dropped on another takes its new parent's board values with its children`() {
        objects = listOf(
            item("p", "task", "doing", "a0", "Parent"),
            item("x", "bug", "todo", "a2", "Mover"),
            item("y", "bug", "todo", "a3", "Under", parent = "x"),
        ).joinToString(",")
        val model = project("v2")
        listing = false
        updated = """{"success": true, "parent": "p", "ranks": [{"id": "x", "rank": "a1", "updated": 77}], "updated": 77}"""
        handleTreeDrop("x", TreeNode(model.uiState.value.objects.first { obj -> obj.id == "p" }, 0, hasChildren = false, isExpanded = true), DragEdge.On, model)
        until { model.uiState.value.objects.first { obj -> obj.id == "x" }.rank == "a1" }
        val drawn = model.uiState.value.objects.associateBy { obj -> obj.id }
        assertEquals("p", drawn.getValue("x").parent)
        assertEquals("THE POINT: Parent's status, as the server gives it, though the list cannot be read again", "doing", drawn.getValue("x").stringValue("status"))
        assertEquals("THE POINT: and its child's", "doing", drawn.getValue("y").stringValue("status"))
    }

    // ---- One move at a time ----

    private fun moves(id: String) = asked.filter { (path, _) -> path == "p1/-/objects/$id/move" }.map { (_, body) -> JsonParser.parseString(body).asJsonObject }

    @Test
    fun `moves still waiting when the board closes are sent, in turn`() {
        val store = ViewModelStore()
        val model = ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ProjectViewModel(
                SavedStateHandle(mapOf("projectId" to "p1")),
                repository,
                socket,
                session,
                ActiveViewStore(context),
                CollapsedColumnsStore(context),
            ) as T
        })[ProjectViewModel::class.java]
        until { model.uiState.value.projectDetails != null && !model.uiState.value.isLoading }
        model.setActiveView("v1")
        settle()
        listing = false
        answering = CountDownLatch(1)
        model.moveObject("h", "status", "todo", after = "a")
        model.moveObject("g", "status", "todo", after = "h")
        until { moves("h").isNotEmpty() }
        // Back, while the first is still being written.
        store.clear()
        answering?.countDown()
        until { moves("g").isNotEmpty() }
        assertEquals("THE POINT: the second is sent once the first is answered, though the board has gone", "h", moves("g").single()["after"]?.asString)
    }

    @Test
    fun `a list read while a move is answered lands without taking it back`() {
        val model = project()
        answering = CountDownLatch(1)
        answer = """{"success": true, "ranks": [{"id": "h", "rank": "a0V", "updated": 99}], "updated": 99}"""
        val read = CountDownLatch(1)
        reading = read
        model.moveObject("h", "status", "todo", after = "a")
        until { moves("h").isNotEmpty() }
        // A pull to refresh, read before the move is written.
        model.refresh()
        until { asked.count { (path, _) -> path == "p1/-/objects" } >= 2 }
        answering?.countDown()
        until { model.uiState.value.objects.first { obj -> obj.id == "h" }.rank == "a0V" }
        // Every list read from now on fails, so only the stale one lands.
        listing = false
        read.countDown()
        until { !model.uiState.value.isRefreshing }
        settle()
        assertEquals("THE POINT: the list read before the answer does not put it back in Doing", "todo", status(model, "h"))
        assertEquals("a0V", model.uiState.value.objects.first { obj -> obj.id == "h" }.rank)
    }

    @Test
    fun `two quick moves of one card go one after the other, in the order made`() {
        val model = project()
        listing = false
        answering = CountDownLatch(1)
        model.moveObject("h", "status", "todo", after = "a")
        model.moveObject("h", "status", "", clear = true)
        settle()
        assertEquals("THE POINT: the second waits for the first's answer", 1, moves("h").size)
        answering?.countDown()
        until { moves("h").size == 2 }
        assertEquals("todo", moves("h")[0]["value"]?.asString)
        assertEquals("then the column for no value", "true", moves("h")[1]["clear"]?.asString)
        settle()
        assertEquals("", status(model, "h"))
    }

    @Test
    fun `a move waiting beside a card whose own move fails goes beside its other neighbour`() {
        val model = project()
        listing = false
        answering = CountDownLatch(1)
        refused = setOf("p1/-/objects/g/move")
        model.moveObject("g", "status", "todo", after = "a")
        // Hidden dropped between Gamma, drawn after Alpha, and Beta.
        model.moveObject("h", "status", "todo", after = "g", before = "b")
        assertEquals(listOf("a", "g", "h", "c", "b"), todo(model))
        answering?.countDown()
        until { moves("h").isNotEmpty() }
        assertFalse("THE POINT: not beside Gamma, which is back in Doing", moves("h").single().has("after"))
        assertEquals("b", moves("h").single()["before"]?.asString)
        until { failures.isNotEmpty() }
        settle()
        assertEquals("doing", status(model, "g"))
        assertEquals("and drawn where it goes, above Beta", listOf("a", "c", "h", "b"), todo(model))
    }

    @Test
    fun `a move still waiting stays where it was dropped through a reload`() {
        val model = project()
        answering = CountDownLatch(1)
        model.moveObject("h", "status", "todo", after = "a")
        model.refresh()
        until { !model.uiState.value.isRefreshing }
        assertEquals("THE POINT: the reload, which does not hold the move yet, is drawn under it", "todo", status(model, "h"))
        assertEquals(listOf("a", "h", "c", "b"), todo(model))
        answering?.countDown()
    }

    @Test
    fun `a row dropped on another takes the key the server gives it there`() {
        val model = project("v2")
        listing = false
        updated = """{"success": true, "parent": "b", "ranks": [{"id": "c", "rank": "a9", "updated": 77}], "updated": 77}"""
        val beta = model.uiState.value.objects.first { obj -> obj.id == "b" }
        handleTreeDrop("c", TreeNode(beta, 0, hasChildren = false, isExpanded = false), DragEdge.On, model)
        until { model.uiState.value.objects.first { obj -> obj.id == "c" }.rank == "a9" }
        val child = model.uiState.value.objects.first { obj -> obj.id == "c" }
        assertEquals("THE POINT: under Beta, after its children, though the list cannot be read again", "b", child.parent)
        assertEquals(77L, child.updated)
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
            val state by model.uiState.collectAsState()
            BoardView(
                objects = state.objects,
                view = model.getActiveView(),
                viewModel = model,
                projectDetails = state.projectDetails,
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

    // ---- A change to the project ----

    /** [titles] as the board draws them, top to bottom. */
    private fun drawn(vararg titles: String): List<String> =
        titles.sortedBy { title -> rule.onNodeWithText(title).getBoundsInRoot().top.value }

    /**
     * The board's objects held as they were: a change to the project's
     * details alone, the options a sort ranks by, reaches the board through
     * the details the screen collects.
     */
    @Test
    fun `a board sorted by a field sorts again when the field's options change`() {
        objects = listOf(
            item("a", "task", "todo", "a0", "Alpha", size = "large"),
            item("b", "task", "todo", "a1", "Beta", size = "small"),
        ).joinToString(",")
        val model = project()
        model.setSortField("field:size")
        val held = model.uiState.value.objects
        val view = model.getActiveView()
        rule.setContent {
            val state by model.uiState.collectAsState()
            BoardView(objects = held, view = view, viewModel = model, projectDetails = state.projectDetails, onObjectClick = {})
        }
        rule.waitForIdle()
        assertEquals("small first", listOf("Beta", "Alpha"), drawn("Alpha", "Beta"))
        sizes = """[{"id": "small", "name": "Small", "rank": 2}, {"id": "large", "name": "Large", "rank": 1}]"""
        model.refresh()
        until { model.getOptionsForObject("task", "size").any { option -> option.id == "large" && option.rank == 1 } }
        rule.waitForIdle()
        assertEquals("THE POINT: large first, as the options now rank them", listOf("Alpha", "Beta"), drawn("Alpha", "Beta"))
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
