// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.`object`

import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import org.mochios.android.model.Comment
import org.mochios.android.ui.components.CommentDrafts
import org.mochios.android.ui.components.LoadState
import org.mochios.android.util.fingerprint
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.mochios.projects.R
import org.mochios.projects.api.ProjectsApi
import org.mochios.projects.model.Activity
import org.mochios.projects.model.ActivityDisplay
import org.mochios.projects.model.Project
import org.mochios.projects.model.ProjectClass
import org.mochios.projects.model.ProjectDetails
import org.mochios.projects.model.ProjectField
import org.mochios.projects.model.ProjectObject
import org.mochios.projects.repository.ProjectsRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDate
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import org.mochios.android.R as MochiR

/**
 * An object's sheet: its history read a page at a time and named for what it
 * is, the merge check's failure and the merge itself, values formatted by
 * type in the user's zone and number format, and nothing offered that the
 * reader's access does not allow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class ObjectFixesTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var model: ObjectDetailViewModel

    @Volatile private var live: WebSocket? = null

    /** The object's history, newest first. */
    @Volatile private var history = (149 downTo 0).map { index -> "e$index" }

    /** Whether reading the history fails. */
    @Volatile private var historyDown = false

    /**
     * The merge check's failure, as status and reason: 502 when the
     * repositories service is unavailable, 409 when it answers an error; null
     * for an answer.
     */
    @Volatile private var checkFailure: Pair<Int, String>? = null

    /** Whether the merge request is a draft. */
    @Volatile private var draft = false

    /** Whether the merge answers that it recorded the request. */
    @Volatile private var records = true

    private val asked = ConcurrentLinkedQueue<Pair<String, String>>()

    private val format = Format(UserPreferences(dateFormat = DateFormat.DD_DOT_MM_YYYY, timezone = "America/Los_Angeles"))

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                val query = request.requestUrl
                if (path == "/_/websocket") {
                    return MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            live = webSocket
                        }

                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            webSocket.close(code, reason)
                        }
                    })
                }
                asked.add(request.path.orEmpty() to request.body.readUtf8())
                return when (path) {
                    "/projects/p1/-/objects/o1" -> ok("""{"object": {"id": "o1", "project": "p1", "class": "task"}, "values": {"title": "Old"}}""")
                    "/projects/p1/-/objects/o1/activity" -> {
                        if (historyDown) return MockResponse().setResponseCode(500).setBody("""{"error": "History unavailable"}""")
                        val limit = query?.queryParameter("limit")?.toInt() ?: 100
                        val offset = query?.queryParameter("offset")?.toInt() ?: 0
                        val page = history.drop(offset).take(limit).joinToString(",") { id ->
                            """{"id": "$id", "user": "u1", "name": "Ann", "action": "commented", "created": 1, "display": {}}"""
                        }
                        ok("""{"activities": [$page]}""")
                    }
                    "/projects/-/repositories" -> ok("""{"repositories": [{"id": "repo1", "name": "Website"}]}""")
                    "/projects/p1/-/objects/o1/requests" ->
                        ok("""{"requests": [{"id": "r1", "object": "o1", "repository": "repo1", "source": "fix", "target": "main", "status": "open", "title": "Fix the header", "draft": $draft}]}""")
                    "/projects/-/repositories/repo1/merge/check" ->
                        checkFailure?.let { (status, reason) ->
                            MockResponse().setResponseCode(status).setBody("""{"error": "$reason"}""")
                        } ?: ok("""{"mergeable": true, "conflicts": [], "ahead": 1, "behind": 0}""")
                    "/projects/-/repositories/repo1/merge" ->
                        ok(if (records) """{"success": true, "recorded": true}""" else """{"success": true}""")
                    else -> ok("{}")
                }
            }
        }
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        val api = Retrofit.Builder()
            .baseUrl(server.url("/projects/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ProjectsApi::class.java)
        val session = SessionManager(context)
        runBlocking { session.setServerUrl(base) }
        socket = MochiWebSocket(OkHttpClient(), Gson(), object : SocketSession {
            override suspend fun serverUrl(): String = base

            override suspend fun token(app: String): String? = "token"

            override suspend fun invalidate(app: String) {
            }
        })
        model = ObjectDetailViewModel(ProjectsRepository(api, FileStore(context), context, session), session, socket)
    }

    @After
    fun end() {
        socket.disconnectAll()
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    private fun settle() {
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
    }

    private fun open(access: String = "owner") {
        model.loadWithInitialObject("p1", "o1", ProjectObject(id = "o1", project = "p1", objectClass = "task"), access)
        until { model.uiState.value.obj != null && live != null && socket.isLive("p1") }
        settle()
    }

    private fun asked(path: String) = asked.filter { (asked, _) -> asked.substringBefore("?") == path }

    private fun shows(text: String) = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun described(text: String) = rule.onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty()

    private val details = ProjectDetails(
        project = Project(id = "p1", prefix = "B", access = "owner"),
        classes = listOf(ProjectClass(id = "task", name = "Task", title = "title")),
        fields = mapOf("task" to listOf(ProjectField(id = "title", name = "Title", fieldtype = "text"))),
    )

    // ---- History ----

    @Test
    fun `the history is read a page at a time, and a refresh keeps the older pages`() {
        open()
        until { model.uiState.value.activity.size == 100 }
        assertTrue("the first page asks for the web's page", asked.any { (path, _) -> path.endsWith("/activity?limit=100&offset=0") })
        model.loadMoreActivity()
        until { asked("/projects/p1/-/objects/o1/activity").size == 2 && model.uiState.value.activityLoad == LoadState.Idle }
        assertTrue("THE POINT: the next page is asked for from where the first ended", asked.any { (path, _) -> path.endsWith("/activity?limit=100&offset=100") })
        assertEquals(150, model.uiState.value.activity.size)
        assertFalse("a short page is the last", model.uiState.value.activityMore)
        history = listOf("fresh") + history
        live!!.send("""{"type": "values/update", "id": "o1"}""")
        until { model.uiState.value.activity.firstOrNull()?.id == "fresh" }
        assertEquals("THE POINT: the refresh keeps every page read, each entry once", 151, model.uiState.value.activity.size)
        assertEquals(151, model.uiState.value.activity.map { entry -> entry.id }.toSet().size)
    }

    @Test
    fun `a history that cannot be read says why, not that there is none`() {
        historyDown = true
        open()
        until { asked("/projects/p1/-/objects/o1/activity").isNotEmpty() }
        settle()
        assertTrue("THE POINT: the failure is kept", model.uiState.value.activityLoad is LoadState.Failed)
        rule.setContent {
            val state = model.uiState.value
            ActivityTab(activity = state.activity, load = state.activityLoad, more = state.activityMore, onLoadMore = model::loadMoreActivity, fields = emptyList())
        }
        rule.waitForIdle()
        assertTrue("THE POINT: the reason", shows("History unavailable"))
        assertFalse(shows(context.getString(R.string.projects_activity_empty)))
        historyDown = false
        rule.onNodeWithText(context.getString(MochiR.string.common_retry)).performClick()
        until { model.uiState.value.activity.size == 100 }
    }

    @Test
    fun `each entry names what it changed as a reader knows it`() {
        val fields = listOf(
            ProjectField(id = "title", name = "Title", fieldtype = "text"),
            ProjectField(id = "steps", name = "Steps", fieldtype = "checklist"),
            ProjectField(id = "signed", name = "Signed off", fieldtype = "checkbox"),
            ProjectField(id = "due", name = "Due", fieldtype = "date"),
            ProjectField(id = "size", name = "Size", fieldtype = "number"),
            ProjectField(id = "owner", name = "Owner", fieldtype = "user"),
            ProjectField(id = "status", name = "Status", fieldtype = "enumerated"),
        )
        val entries = listOf(
            Activity(id = "moved", action = "moved", field = "parent", oldvalue = "x", newvalue = "y", display = ActivityDisplay(old = "Alpha", new = "Beta")),
            Activity(id = "top", action = "moved", field = "parent", oldvalue = "x", newvalue = ""),
            Activity(id = "linked", action = "linked", field = "blocks", newvalue = "t", display = ActivityDisplay(new = "Target")),
            Activity(id = "class", action = "updated", field = "class", oldvalue = "task", newvalue = "bug", display = ActivityDisplay(old = "Task", new = "Bug")),
            Activity(id = "owner", action = "updated", field = "owner", oldvalue = "", newvalue = "u2", display = ActivityDisplay(field = "Owner", type = "user", new = "Bea")),
            Activity(id = "stranger", action = "updated", field = "owner", newvalue = "u3"),
            Activity(id = "steps", action = "updated", field = "steps", newvalue = """[{"text":"One","done":true},{"text":"Two"}]"""),
            Activity(id = "signed", action = "updated", field = "signed", oldvalue = "0", newvalue = "1"),
            Activity(id = "due", action = "updated", field = "due", newvalue = "2026-11-17"),
            Activity(id = "size", action = "updated", field = "size", newvalue = "1234.5"),
            Activity(id = "status", action = "updated", field = "status", newvalue = "opt1"),
            Activity(id = "title", action = "updated", field = "title", newvalue = "opt1"),
            Activity(id = "elsewhere", action = "updated", field = "other", newvalue = "v"),
            Activity(id = "gone", action = "linked", field = "relates", newvalue = "deadobject", display = ActivityDisplay()),
        )
        val lines = mutableMapOf<String, ActivityLine>()
        rule.setContent {
            CompositionLocalProvider(LocalFormat provides format) {
                entries.forEach { entry -> lines[entry.id] = activityLine(entry, fields) }
            }
        }
        rule.waitForIdle()
        val unknown = context.getString(R.string.projects_activity_unknown)
        assertEquals(ActivityLine(context.getString(R.string.projects_activity_moved), "Alpha", "Beta"), lines["moved"])
        assertEquals("a move to the top level", context.getString(R.string.projects_parent_none), lines["top"]!!.after)
        assertEquals(unknown, lines["top"]!!.before)
        assertEquals(ActivityLine(context.getString(R.string.projects_links_type_blocks), null, "Target"), lines["linked"])
        assertEquals(ActivityLine(context.getString(R.string.projects_activity_class), "Task", "Bug"), lines["class"])
        assertEquals(ActivityLine(context.getString(R.string.projects_activity_updated, "Owner"), null, "Bea"), lines["owner"])
        assertEquals("THE POINT: a person never by id", context.getString(MochiR.string.person_unknown), lines["stranger"]!!.after)
        assertEquals("a checklist as its progress", "1/2", lines["steps"]!!.after)
        assertEquals(context.getString(R.string.projects_field_yes), lines["signed"]!!.after)
        assertEquals(context.getString(R.string.projects_field_no), lines["signed"]!!.before)
        assertEquals("a date as the stored day", format.formatDate(LocalDate.of(2026, 11, 17)), lines["due"]!!.after)
        assertEquals("1,234.5", lines["size"]!!.after)
        assertEquals("an option never by id", unknown, lines["status"]!!.after)
        assertEquals("a text that looks like an option id stays text", "opt1", lines["title"]!!.after)
        assertEquals("a field of another class is not named", context.getString(R.string.projects_activity_made_change), lines["elsewhere"]!!.phrase)
        assertEquals("a link to an object gone is never its id", unknown, lines["gone"]!!.after)
    }

    // ---- Merge requests ----

    private fun requestsTab(access: String = "owner") {
        open(access)
        model.ensureRepositories()
        until { model.uiState.value.requests.isNotEmpty() && model.uiState.value.repositories.isNotEmpty() }
        rule.setContent {
            val state by model.uiState.collectAsState()
            RequestsTab(requests = state.requests, projectId = "p1", viewModel = model, onViewDiff = { _, _, _, _ -> })
        }
        rule.waitForIdle()
    }

    @Test
    fun `a merge check that could not compare the branches says why, with a retry`() {
        checkFailure = 502 to "Repositories are unavailable"
        requestsTab()
        rule.onNodeWithText("Fix the header").performClick()
        rule.waitForIdle()
        until { asked("/projects/-/repositories/repo1/merge/check").isNotEmpty() && !model.isCheckingMerge.value }
        rule.waitForIdle()
        assertTrue("THE POINT: the reason", shows("Repositories are unavailable"))
        assertFalse("not an answer that they do not merge", shows(context.getString(R.string.projects_request_cannot_merge)))
        checkFailure = 409 to "No common ancestor"
        rule.onNodeWithText(context.getString(MochiR.string.common_retry)).performClick()
        rule.waitForIdle()
        until { asked("/projects/-/repositories/repo1/merge/check").size == 2 && model.mergeCheckError.value != null }
        rule.waitForIdle()
        assertTrue("a refusal of the service says its reason too", shows("No common ancestor"))
        checkFailure = null
        rule.onNodeWithText(context.getString(MochiR.string.common_retry)).performClick()
        rule.waitForIdle()
        until { model.mergeCheck.value != null }
        rule.waitForIdle()
        assertTrue(shows(context.getString(R.string.projects_request_can_merge)))
    }

    @Test
    fun `a draft marked ready has its merge checked`() {
        draft = true
        requestsTab()
        rule.onNodeWithText("Fix the header").performClick()
        rule.waitForIdle()
        settle()
        assertTrue("a draft is not checked", asked("/projects/-/repositories/repo1/merge/check").isEmpty())
        draft = false
        rule.onNodeWithText(context.getString(R.string.projects_request_mark_ready)).performClick()
        rule.waitForIdle()
        until { model.uiState.value.requests.single().draft == false }
        rule.waitForIdle()
        settle()
        assertTrue("THE POINT: checked once ready", asked("/projects/-/repositories/repo1/merge/check").isNotEmpty())
    }

    @Test
    fun `a merge records its request in the same call`() {
        open()
        model.performMerge("repo1", "fix", "main", "Merge", "merge", "r1")
        until { model.mergeSuccess.value }
        assertTrue("THE POINT: the merge names its request", asked("/projects/-/repositories/repo1/merge").single().second.contains("request=r1"))
        settle()
        assertTrue("and no second call sets its status", asked("/projects/p1/-/objects/o1/requests/r1/update").isEmpty())
    }

    @Test
    fun `a merge on a server that does not record it sets the status itself`() {
        records = false
        open()
        model.performMerge("repo1", "fix", "main", "Merge", "merge", "r1")
        until { model.mergeSuccess.value }
        settle()
        assertEquals(
            "THE POINT: the older server still has the request merged",
            1,
            asked("/projects/p1/-/objects/o1/requests/r1/update").count { (_, body) -> body.contains("status=merged") },
        )
    }

    @Test
    fun `a reader is offered no new request and no request delete`() {
        requestsTab(access = "view")
        assertFalse(described(context.getString(R.string.projects_request_new)))
        assertFalse(described(context.getString(MochiR.string.common_more_options)))
    }

    // ---- Values ----

    private fun editor(field: ProjectField, value: String, canWrite: Boolean, sent: MutableList<String> = mutableListOf()) {
        rule.setContent {
            CompositionLocalProvider(LocalFormat provides format) {
                FieldEditor(
                    field = field,
                    value = value,
                    options = emptyList(),
                    canWrite = canWrite,
                    people = emptyList(),
                    onValueChange = { typed -> sent += typed },
                    onSearchUsers = { emptyList() },
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `a date shows the day stored, west of UTC and before 1970`() {
        val due = ProjectField(id = "due", name = "Due", fieldtype = "date")
        editor(due, "2026-11-17", canWrite = false)
        assertTrue("THE POINT: not the day before in Los Angeles", shows(format.formatDate(LocalDate.of(2026, 11, 17))))
    }

    @Test
    fun `a date before 1970 is shown`() {
        editor(ProjectField(id = "born", name = "Born", fieldtype = "date"), "1965-03-02", canWrite = false)
        assertTrue("THE POINT: not left blank", shows(format.formatDate(LocalDate.of(1965, 3, 2))))
    }

    @Test
    fun `a read-only number is in the user's number format`() {
        editor(ProjectField(id = "size", name = "Size", fieldtype = "number"), "1234.5", canWrite = false)
        assertTrue(shows("1,234.5"))
    }

    @Test
    fun `a number takes a minus and a point on the way, and saves only a number`() {
        val sent = mutableListOf<String>()
        editor(ProjectField(id = "size", name = "Size", fieldtype = "number"), "", canWrite = true, sent = sent)
        rule.onNodeWithText("Size").performTextReplacement("-")
        rule.waitForIdle()
        assertTrue("THE POINT: the minus stays, and nothing is saved yet", shows("-") && sent.isEmpty())
        rule.onNodeWithText("Size").performTextReplacement("-.5")
        rule.waitForIdle()
        assertEquals("a whole number goes", listOf("-0.5"), sent)
    }

    @Test
    fun `a person the project does not name is shown by fingerprint`() {
        val stranger = "1StrangerEntityIdNotInThePeopleListAtAll000000000"
        editor(ProjectField(id = "owner", name = "Owner", fieldtype = "user"), stranger, canWrite = false)
        assertTrue("THE POINT: by fingerprint", shows(formatFingerprint(fingerprint(stranger))))
        assertFalse("never by id", shows(stranger))
    }

    // ---- Access ----

    private fun properties(access: String) {
        open(access)
        rule.setContent {
            PropertiesTab(obj = model.uiState.value.obj!!, projectDetails = details, viewModel = model, projectId = "p1")
        }
        rule.waitForIdle()
    }

    @Test
    fun `a reader adds no attachment`() {
        properties("view")
        assertFalse("THE POINT", described(context.getString(R.string.projects_attachment_add)))
    }

    @Test
    fun `a writer adds attachments`() {
        properties("write")
        assertTrue(described(context.getString(R.string.projects_attachment_add)))
    }

    private fun comments(canComment: Boolean) {
        rule.setContent {
            CommentsTab(
                comments = listOf(Comment(id = "first", author = "ann", body = "as posted", authorName = "Ann")),
                currentUserId = "bob",
                projectId = "p1",
                drafts = CommentDrafts(),
                target = "o1",
                onCreateComment = { _, _, _ -> },
                canComment = canComment,
                resolveFileName = { "" },
                onUpdateComment = { _, _ -> },
                onDeleteComment = {},
            )
        }
        rule.waitForIdle()
    }

    @Test
    fun `a reader without comment access has no composer and no reply`() {
        comments(canComment = false)
        assertFalse("THE POINT: no composer", shows(context.getString(R.string.projects_comment_placeholder)))
        assertFalse("no reply", described(context.getString(MochiR.string.comment_reply)))
    }

    @Test
    fun `a commenter has the composer and replies`() {
        comments(canComment = true)
        assertTrue(shows(context.getString(R.string.projects_comment_placeholder)))
        assertTrue(described(context.getString(MochiR.string.comment_reply)))
    }

    @Test
    fun `a reader's sheet offers no copy or delete`() {
        open("view")
        rule.setContent {
            ObjectDetailSheet(
                projectId = "p1",
                objectId = "o1",
                projectDetails = details.copy(project = details.project.copy(access = "view")),
                initialObject = model.uiState.value.obj,
                onDismiss = {},
                onDeleteObject = {},
                onViewDiff = { _, _, _, _ -> },
                viewModel = model,
            )
        }
        rule.waitForIdle()
        assertFalse("THE POINT", described(context.getString(MochiR.string.common_more_options)))
    }
}
