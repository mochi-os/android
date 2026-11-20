// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.project

import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import org.mochios.android.ui.components.dnd.DragEdge
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.mochios.projects.R
import org.mochios.projects.api.ProjectsApi
import org.mochios.projects.lib.ActiveViewStore
import org.mochios.projects.lib.CollapsedColumnsStore
import org.mochios.projects.repository.ProjectsRepository
import org.mochios.projects.ui.board.BoardView
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

    private val asked = ConcurrentLinkedQueue<Pair<String, String>>()
    private val failures = mutableListOf<MochiError>()
    private val jobs = mutableListOf<Job>()

    private fun item(id: String, cls: String, status: String, rank: String, title: String, parent: String = "", number: Int = 1) =
        """{"id": "$id", "project": "p1", "class": "$cls", "parent": "$parent", "rank": "$rank", "number": $number,
            "values": {"title": "$title", "status": "$status"}}"""

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
                val data = when (short) {
                    "p1/-/information" -> """{"project": {"id": "p1", "name": "Board", "prefix": "B", "access": "$access"},
                        "classes": [{"id": "task", "name": "Task", "title": "title", "rank": 1}, {"id": "bug", "name": "Bug", "title": "title", "rank": 2}],
                        "fields": {"task": [{"id": "title", "name": "Title", "fieldtype": "text", "rank": 1}, {"id": "status", "name": "Status", "fieldtype": "enumerated", "rank": 2}],
                            "bug": [{"id": "title", "name": "Title", "fieldtype": "text", "rank": 1}, {"id": "status", "name": "Status", "fieldtype": "enumerated", "rank": 2}]},
                        "options": {"task": {"status": $options}, "bug": {"status": $options}},
                        "views": [{"id": "v1", "name": "Board", "viewtype": "board", "columns": "status", "classes": ["task"], "filter": "status:todo"},
                            {"id": "v2", "name": "List", "viewtype": "list"}],
                        "hierarchy": {"task": [""], "bug": ["", "task", "bug"]}}"""
                    "p1/-/objects" -> """{"objects": [$objects], "watched": []}"""
                    "p1/-/people" -> """{"people": []}"""
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
    fun `a card is placed among the column as the server ranks it`() {
        val model = project()
        assertEquals(listOf("a", "c", "b"), model.rankedColumn("status", "todo", "h"))
        assertEquals(
            "THE POINT: above Beta is third, after the child the board does not show",
            3,
            model.dropPosition("status", "todo", target = "b", moved = "h", below = false),
        )
    }

    @Test
    fun `a sort on the board does not change where a drop lands`() {
        val model = project()
        model.setSortField("number")
        model.setSortDirection("desc")
        assertEquals(3, model.dropPosition("status", "todo", target = "b", moved = "h", below = false))
    }

    @Test
    fun `a drop with no column value is placed among the moved card's own column`() {
        val model = project()
        assertEquals(listOf("c", "b"), model.rankedColumn("status", "", "a"))
    }

    @Test
    fun `a drop at the end of a column lands after every object it holds`() {
        val model = project()
        model.moveToEnd("h", "status", "todo")
        until { asked.any { (path, _) -> path == "p1/-/objects/h/move" } }
        val rank = sent("p1/-/objects/h/move")["rank"].asLong
        assertTrue("THE POINT: $rank is past the three objects the column holds", rank >= 4)
    }

    @Test
    fun `top-level rows reorder by drag`() {
        val model = project("v2")
        val alpha = model.uiState.value.objects.first { obj -> obj.id == "a" }
        handleTreeDrop("b", TreeNode(alpha, 0, hasChildren = true, isExpanded = true), DragEdge.Top, model)
        until { asked.any { (path, _) -> path == "p1/-/objects/b/move" } }
        val move = sent("p1/-/objects/b/move")
        assertEquals("THE POINT: the top level goes as an empty scope", "", move["scope"]?.asString)
        assertEquals(1, move["rank"].asInt)
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
            { model.moveObject("a", "status", "doing", 1) },
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
