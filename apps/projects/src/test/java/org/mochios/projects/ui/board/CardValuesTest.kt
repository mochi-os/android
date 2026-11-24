// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.board

import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import com.google.gson.Gson
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.format.formatFingerprint
import org.mochios.android.i18n.AppContext
import org.mochios.android.i18n.DateFormat
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.UserPreferences
import org.mochios.android.util.fingerprint
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.mochios.projects.R
import org.mochios.projects.api.ProjectsApi
import org.mochios.projects.lib.ActiveViewStore
import org.mochios.projects.lib.CollapsedColumnsStore
import org.mochios.projects.repository.ProjectsRepository
import org.mochios.projects.ui.project.ProjectScreen
import org.mochios.projects.ui.project.ProjectViewModel
import org.mochios.projects.ui.projectlist.ProjectListViewModel
import org.mochios.projects.ui.tree.TreeView
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import org.mochios.android.R as MochiR

/**
 * A card and a row write each value by its field's type, never a person's id;
 * an object is named by its title or readable id; what a reader may not do is
 * not offered; the route's object opens once; and the drawer lists every
 * project whatever the list is searched for.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class CardValuesTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var session: SessionManager
    private lateinit var repository: ProjectsRepository

    /** The project's access level, as the information answers it. */
    @Volatile private var access = "owner"

    /** The project's prefix. */
    @Volatile private var prefix = "B"

    /** A person the project's people do not name. */
    private val stranger = "1StrangerEntityIdNotInThePeopleListAtAll000000000"

    private val format = Format(UserPreferences(dateFormat = DateFormat.DD_DOT_MM_YYYY))

    /** The project's objects. */
    @Volatile private var objects = """
        {"id": "a", "project": "p1", "class": "task", "parent": "", "rank": "a0", "number": 1,
            "values": {"title": "Alpha", "status": "todo", "due": "2026-01-05",
                "steps": "[{\"text\":\"One\",\"done\":true},{\"text\":\"Two\",\"done\":false}]",
                "signed": "1", "size": "12345.5", "owner": "known"}},
        {"id": "b", "project": "p1", "class": "task", "parent": "", "rank": "a1", "number": 2,
            "values": {"title": "Beta", "status": "todo", "owner": "$stranger", "signed": "0"}},
        {"id": "n", "project": "p1", "class": "task", "parent": "", "rank": "a2", "number": 3,
            "values": {"status": "todo"}},
        {"id": "c", "project": "p1", "class": "bug", "parent": "", "rank": "a3", "number": 4,
            "values": {"title": "Crash", "status": "todo"}}
    """

    @Before
    fun begin() {
        AppContext.set(context)
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
                val field = { id: String, name: String, type: String, rank: Int ->
                    """{"id": "$id", "name": "$name", "fieldtype": "$type", "rank": $rank, "card": 1}"""
                }
                val fields = listOf(
                    field("title", "Title", "text", 1), field("status", "Status", "enumerated", 2),
                    field("due", "Due", "date", 3), field("steps", "Steps", "checklist", 4),
                    field("signed", "Signed off", "checkbox", 5), field("size", "Size", "number", 6),
                    field("owner", "Owner", "user", 7),
                ).joinToString(",")
                val options = """{"status": [{"id": "todo", "name": "To do", "rank": 1}]}"""
                val data = when (path.substringAfter("/projects/")) {
                    "p1/-/information" -> """{"project": {"id": "p1", "name": "Board", "prefix": "$prefix", "access": "$access"},
                        "classes": [{"id": "task", "name": "Task", "title": "title", "rank": 1}, {"id": "bug", "name": "Bug", "title": "title", "rank": 2}],
                        "fields": {"task": [$fields], "bug": [$fields]},
                        "options": {"task": $options, "bug": $options},
                        "views": [{"id": "v1", "name": "Board", "viewtype": "board", "columns": "status", "classes": ["task"]},
                            {"id": "v2", "name": "List", "viewtype": "list"}],
                        "hierarchy": {"task": [""], "bug": ["", "task"]}}"""
                    "p1/-/objects" -> """{"objects": [$objects], "watched": []}"""
                    "p1/-/people" -> """{"people": [{"id": "known", "name": "Known Person"}]}"""
                    "-/list" -> """{"projects": [{"id": "p1", "fingerprint": "p1", "name": "Alpha project"}, {"id": "p2", "fingerprint": "p2", "name": "Beta project"}]}"""
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

    private fun project(view: String, handle: SavedStateHandle = SavedStateHandle(mapOf("projectId" to "p1"))): ProjectViewModel {
        val model = ProjectViewModel(handle, repository, socket, session, ActiveViewStore(context), CollapsedColumnsStore(context))
        until { model.uiState.value.projectDetails != null && model.uiState.value.people.isNotEmpty() }
        model.setActiveView(view)
        return model
    }

    private fun board(model: ProjectViewModel) {
        rule.setContent {
            CompositionLocalProvider(LocalFormat provides format) {
                val state by model.uiState.collectAsState()
                BoardView(
                    objects = state.objects,
                    view = model.getActiveView(),
                    viewModel = model,
                    projectDetails = state.projectDetails,
                    onObjectClick = {},
                    onCreateObject = {},
                )
            }
        }
        rule.waitForIdle()
    }

    private fun tree(model: ProjectViewModel) {
        rule.setContent {
            CompositionLocalProvider(LocalFormat provides format) {
                TreeView(
                    objects = model.getFilteredObjects(),
                    view = model.getActiveView(),
                    viewModel = model,
                    onObjectClick = {},
                )
            }
        }
        rule.waitForIdle()
    }

    private fun shows(text: String) = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun described(text: String) = rule.onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty()

    private val day get() = format.formatDate(LocalDate.of(2026, 1, 5))

    // ---- Values ----

    @Test
    fun `a card writes each value by its type`() {
        board(project("v1"))
        assertTrue("THE POINT: the date in the user's format", shows(day))
        assertFalse("not as stored", shows("2026-01-05"))
        assertTrue("a checklist as its progress", shows("1/2"))
        assertFalse("not as its JSON", shows("\"text\""))
        assertTrue("a ticked box by its field's name", shows("Signed off"))
        assertTrue("a number in the user's format", shows("12,345.5"))
        assertTrue("a known person by name", shows("Known Person"))
        assertTrue("a stranger by fingerprint", shows(formatFingerprint(fingerprint(stranger))))
        assertFalse("never by id", shows(stranger))
    }

    @Test
    fun `a row writes each value by its type`() {
        tree(project("v2"))
        assertTrue("THE POINT: the date in the user's format", shows(day))
        assertFalse(shows("2026-01-05"))
        assertTrue(shows("1/2"))
        assertTrue(shows("12,345.5"))
        assertTrue(shows(formatFingerprint(fingerprint(stranger))))
        assertFalse("never by id", shows(stranger))
        assertEquals("an unticked box shows nothing", 1, rule.onAllNodesWithText("Signed off").fetchSemanticsNodes().size)
    }

    // ---- Titles ----

    @Test
    fun `an untitled card in a project without a prefix is named by its readable id`() {
        prefix = ""
        board(project("v1"))
        assertTrue("THE POINT: as everywhere else", shows("#3"))
        assertFalse("not a bare dash and the number", shows("-3"))
    }

    @Test
    fun `the tree's move lists each parent by its title`() {
        val model = project("v2")
        tree(model)
        // The bug's row is the last; its menu offers the move.
        rule.onAllNodesWithContentDescription(context.getString(MochiR.string.common_more_options)).fetchSemanticsNodes().size.let { rows ->
            rule.onAllNodesWithContentDescription(context.getString(MochiR.string.common_more_options))[rows - 1].performClick()
        }
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(R.string.projects_tree_move)).performClick()
        rule.waitForIdle()
        assertTrue("THE POINT: a parent by its title", rule.onAllNodesWithText("Alpha").fetchSemanticsNodes().size >= 2)
        assertFalse("not by number alone", shows("#1"))
    }

    // ---- Access ----

    @Test
    fun `each level allows what the levels below it do`() {
        val allowed = mapOf(
            "view" to listOf(false, false, false),
            "comment" to listOf(false, false, false),
            "write" to listOf(false, true, true),
            "design" to listOf(true, true, true),
            "owner" to listOf(true, true, true),
        )
        for ((level, expected) in allowed) {
            access = level
            repository.invalidateCache("p1")
            val model = project("v1")
            assertEquals("THE POINT: $level", expected, listOf(model.canDesign(), model.canWrite(), model.canCreate()))
        }
    }

    @Test
    fun `a reader's board offers no drag, no move and no new card`() {
        access = "view"
        board(project("v1"))
        assertFalse("THE POINT: no card drags", described(context.getString(R.string.projects_drag_card)))
        assertFalse("no column takes a new card", described(context.getString(R.string.projects_board_new)))
        assertFalse("no column header drags", described(context.getString(R.string.projects_drag_column)))
    }

    @Test
    fun `a writer's board moves cards but leaves the columns alone`() {
        access = "write"
        board(project("v1"))
        assertTrue("THE POINT: a card drags", described(context.getString(R.string.projects_drag_card)))
        assertTrue("a column takes a new card", described(context.getString(R.string.projects_board_new)))
        assertFalse("no column header drags", described(context.getString(R.string.projects_drag_column)))
        rule.onAllNodesWithContentDescription(context.getString(MochiR.string.common_more_options))[0].performClick()
        rule.waitForIdle()
        assertFalse("no rename", shows(context.getString(R.string.projects_board_rename)))
    }

    @Test
    fun `a designer renames columns`() {
        board(project("v1"))
        rule.onAllNodesWithContentDescription(context.getString(MochiR.string.common_more_options))[0].performClick()
        rule.waitForIdle()
        assertTrue(shows(context.getString(R.string.projects_board_rename)))
        assertTrue(described(context.getString(R.string.projects_drag_column)))
    }

    @Test
    fun `a reader's rows neither drag nor offer a move or a delete`() {
        access = "view"
        tree(project("v2"))
        assertFalse("THE POINT: no row drags", described(context.getString(R.string.projects_drag_row)))
        assertFalse("no row has a menu", described(context.getString(MochiR.string.common_more_options)))
    }

    @Test
    fun `a writer's rows drag and offer delete`() {
        access = "write"
        tree(project("v2"))
        assertTrue(described(context.getString(R.string.projects_drag_row)))
        rule.onAllNodesWithContentDescription(context.getString(MochiR.string.common_more_options))[0].performClick()
        rule.waitForIdle()
        assertTrue(shows(context.getString(MochiR.string.common_delete)))
    }

    // ---- The route's object ----

    @Test
    fun `the route's object opens once, not on every return`() {
        val handle = SavedStateHandle(mapOf("projectId" to "p1"))
        val model = project("v1", handle)
        model.openInitialObject("a")
        assertEquals("a", model.uiState.value.selectedObjectId)
        model.selectObject(null, refresh = false)
        model.openInitialObject("a")
        assertNull("THE POINT: a return leaves the sheet closed", model.uiState.value.selectedObjectId)
        val recreated = project("v1", handle)
        recreated.openInitialObject("a")
        assertNull("nor does the screen made again over the same route", recreated.uiState.value.selectedObjectId)
    }

    // ---- Drawer ----

    @Test
    fun `the drawer lists every project whatever the list is searched for`() {
        val list = ProjectListViewModel(repository)
        until { list.uiState.value.projects.size == 2 }
        list.updateSearchQuery("Alpha")
        rule.setContent {
            ProjectScreen(
                projectId = "",
                onSelectProject = {},
                onSelectAll = {},
                onFindProjects = {},
                onCreateProject = {},
                onSettings = {},
                onDesign = {},
                onAddColumn = { _, _, _ -> },
                onViewDiff = { _, _, _, _ -> },
                onOpenNotifications = {},
                listViewModel = list,
            )
        }
        rule.waitForIdle()
        assertTrue(shows("Alpha project"))
        assertTrue("THE POINT: the search does not reach the drawer", shows("Beta project"))
    }
}
