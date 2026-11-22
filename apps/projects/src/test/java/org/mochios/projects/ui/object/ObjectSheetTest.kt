// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.`object`

import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.mochios.projects.R
import org.mochios.projects.api.ProjectsApi
import org.mochios.projects.model.Project
import org.mochios.projects.model.ProjectClass
import org.mochios.projects.model.ProjectDetails
import org.mochios.projects.model.ProjectObject
import org.mochios.projects.repository.ProjectsRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.mochios.android.R as MochiR

/**
 * An object's sheet: it follows other people's edits without undoing the
 * user's own, keeps the repositories from one object to the next, and shows
 * every failure of what it does while it is open.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class ObjectSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var model: ObjectDetailViewModel

    /** The server's end of the sheet's socket. */
    @Volatile private var live: WebSocket? = null

    /** The values the server holds for o1. */
    @Volatile private var held = """{"status": "open", "title": "Old"}"""

    /** Holds writes of a value until released. */
    private val saving = CountDownLatch(1)

    /** Holds o1's refetches (every read after the first) until released. */
    @Volatile private var refetching: CountDownLatch? = null

    /** Whether the server refuses link and merge request changes. */
    @Volatile private var refuse = false

    private val counts = ConcurrentHashMap<String, AtomicInteger>()
    private val writes = ConcurrentLinkedQueue<Pair<String, String>>()
    private val failures = mutableListOf<MochiError>()
    private val jobs = mutableListOf<Job>()

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                val count = counts.getOrPut(path) { AtomicInteger() }.incrementAndGet()
                return when {
                    path == "/projects/p1/-/objects/o1" -> {
                        if (count > 1) refetching?.await(5, TimeUnit.SECONDS)
                        ok("""{"object": {"id": "o1", "project": "p1", "class": "task"}, "values": $held}""")
                    }
                    path == "/projects/p1/-/objects/o2" -> ok("""{"object": {"id": "o2", "project": "p1"}, "values": {"title": "Second"}}""")
                    path == "/projects/-/repositories" -> ok("""{"repositories": [{"id": "repo1", "name": "Website"}]}""")
                    path == "/projects/-/repositories/repo1/branches" -> ok("""{"branches": [{"name": "fix"}, {"name": "main"}]}""")
                    path == "/projects/p1/-/objects/o1/requests" ->
                        ok("""{"requests": [{"id": "r1", "object": "o1", "repository": "repo1", "source": "fix", "target": "main", "status": "open", "title": "Fix the header", "draft": false}]}""")
                    path.startsWith("/projects/p1/-/objects/o1/values/") -> {
                        writes.add(path to request.body.readUtf8())
                        saving.await(5, TimeUnit.SECONDS)
                        ok("{}")
                    }
                    path.contains("/links/") || path.contains("/requests/") -> {
                        writes.add(path to request.body.readUtf8())
                        if (refuse) {
                            MockResponse().setResponseCode(400).setBody("""{"error": "Refused"}""")
                        } else {
                            ok("{}")
                        }
                    }
                    path == "/_/websocket" -> MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            live = webSocket
                        }

                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            webSocket.close(code, reason)
                        }
                    })
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
        jobs += CoroutineScope(Dispatchers.Main).launch { model.actionFailed.collect { failures += it } }
    }

    @After
    fun end() {
        saving.countDown()
        refetching?.countDown()
        jobs.forEach { it.cancel() }
        socket.disconnectAll()
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun reads(path: String) = counts[path]?.get() ?: 0

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

    /** Opens o1 with the values the board had for it, and waits for its read and socket. */
    private fun open() {
        model.loadWithInitialObject("p1", "o1", ProjectObject(id = "o1", project = "p1", values = mapOf("status" to "open", "title" to "Old")), "owner")
        until { reads("/projects/p1/-/objects/o1") == 1 && live != null && socket.isLive("p1") }
        settle()
    }

    private fun value(field: String) = model.uiState.value.obj?.values?.get(field)

    @Test
    fun `another person's edit shows in the open sheet`() {
        open()
        held = """{"status": "done", "title": "Old"}"""
        live!!.send("""{"type": "values/update", "id": "o1"}""")
        until { reads("/projects/p1/-/objects/o1") == 2 }
        settle()
        assertEquals("THE POINT: the server's new value replaces the one held", "done", value("status"))
    }

    @Test
    fun `a refetch keeps an edit still being saved`() {
        open()
        model.setValue("title", "Typed")
        until { writes.any { (path, _) -> path.endsWith("/values/title") } }
        held = """{"status": "done", "title": "Old"}"""
        live!!.send("""{"type": "values/update", "id": "o1"}""")
        until { reads("/projects/p1/-/objects/o1") == 2 }
        settle()
        assertEquals("THE POINT: the edit the server has not had stays", "Typed", value("title"))
        assertEquals("the rest follows the server", "done", value("status"))
    }

    @Test
    fun `a refetch of one object does not land on the next`() {
        open()
        refetching = CountDownLatch(1)
        live!!.send("""{"type": "values/update", "id": "o1"}""")
        until { reads("/projects/p1/-/objects/o1") == 2 }
        model.loadWithInitialObject("p1", "o2", ProjectObject(id = "o2", project = "p1", values = mapOf("title" to "Second")))
        until { reads("/projects/p1/-/objects/o2") == 1 }
        settle()
        refetching!!.countDown()
        settle()
        assertEquals("o2", model.uiState.value.obj?.id)
        assertEquals("Second", value("title"))
    }

    @Test
    fun `the repositories stay for the next object opened`() {
        open()
        model.ensureRepositories()
        until { model.uiState.value.repositories.isNotEmpty() }
        model.loadWithInitialObject("p1", "o2", ProjectObject(id = "o2", project = "p1"))
        model.ensureRepositories()
        settle()
        assertEquals(listOf("Website"), model.uiState.value.repositories.map { repository -> repository.name })
    }

    @Test
    fun `a refused link or merge request change is shown while the sheet is open`() {
        open()
        refuse = true
        val actions = listOf<() -> Unit>(
            { model.createLink("o2", "relates") },
            { model.createReverseLink("o2", "blocks") },
            { model.deleteOutgoingLink("o2", "relates") },
            { model.deleteIncomingLink("o2", "blocks") },
            { model.createRequest("repo1", "fix", "main", "Fix", null, false) },
            { model.updateRequest("r1", null, null, null, false) },
            { model.deleteRequest("r1") },
        )
        actions.forEachIndexed { index, action ->
            val before = writes.size
            action()
            until { writes.size > before }
            soon { failures.size > index }
        }
        settle()
        assertEquals("THE POINT: each of the seven is reported", 7, failures.size)
        assertNull("nothing goes to the error only an empty sheet draws", model.uiState.value.error)
    }

    @Test
    fun `the sheet's delete names the object and what goes with it`() {
        var deleted = false
        val details = ProjectDetails(
            project = Project(id = "p1", prefix = "B", access = "owner"),
            classes = listOf(ProjectClass(id = "task", name = "Task", title = "title")),
        )
        rule.setContent {
            ObjectDetailSheet(
                projectId = "p1",
                objectId = "o1",
                projectDetails = details,
                initialObject = ProjectObject(id = "o1", project = "p1", objectClass = "task", values = mapOf("title" to "Old")),
                onDismiss = {},
                onDeleteObject = { deleted = true },
                descendants = 2,
                onViewDiff = { _, _, _, _ -> },
                viewModel = model,
            )
        }
        rule.waitForIdle()
        rule.onAllNodesWithContentDescription(context.getString(MochiR.string.common_more_options))[0].performClick()
        rule.onNodeWithText(context.getString(MochiR.string.common_delete)).performClick()
        rule.waitForIdle()
        val question = context.resources.getQuantityString(R.plurals.projects_object_delete_descendants, 2, "Old", "2")
        rule.onNodeWithText(question).assertExists()
        rule.onNodeWithText(context.getString(MochiR.string.common_delete)).performClick()
        assertTrue(deleted)
    }

    /** The requests tab of o1, its requests read and its repositories in. */
    private fun requestsTab() {
        open()
        model.ensureRepositories()
        until { model.uiState.value.requests.isNotEmpty() && model.uiState.value.repositories.isNotEmpty() }
        rule.setContent {
            RequestsTab(
                requests = model.uiState.value.requests,
                projectId = "p1",
                viewModel = model,
                onViewDiff = { _, _, _, _ -> },
            )
        }
        rule.waitForIdle()
    }

    @Test
    fun `an open merge request offers no close, which the server refuses`() {
        requestsTab()
        rule.onNodeWithText("Fix the header").performClick()
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText(context.getString(R.string.projects_request_view_diff)).fetchSemanticsNodes().isNotEmpty())
        assertTrue(
            "THE POINT: no Close",
            rule.onAllNodesWithText(context.getString(MochiR.string.common_close)).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun `deleting a merge request asks first`() {
        requestsTab()
        rule.onAllNodesWithContentDescription(context.getString(MochiR.string.common_more_options))[0].performClick()
        rule.onNodeWithText(context.getString(MochiR.string.common_delete)).performClick()
        rule.waitForIdle()
        assertTrue(
            "THE POINT: nothing is deleted before the answer",
            writes.none { (path, _) -> path.endsWith("/requests/r1/delete") },
        )
        rule.onNodeWithText(context.getString(R.string.projects_request_delete_message)).assertExists()
        rule.onNodeWithText(context.getString(MochiR.string.common_delete)).performClick()
        until { writes.any { (path, _) -> path.endsWith("/requests/r1/delete") } }
    }

    @Test
    fun `a new merge request reports whether the server took it, so its dialog can stay`() {
        open()
        val answers = mutableListOf<Boolean>()
        refuse = true
        model.createRequest("repo1", "fix", "main", "Fix", null, false) { created -> answers += created }
        until { answers.size == 1 }
        refuse = false
        model.createRequest("repo1", "fix", "main", "Fix", null, false) { created -> answers += created }
        until { answers.size == 2 }
        assertEquals("THE POINT: a refusal keeps the dialog, a success closes it", listOf(false, true), answers)
    }
}
