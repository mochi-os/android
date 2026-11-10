// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.mochios.projects.api.ProjectsApi
import org.mochios.projects.lib.ActiveViewStore
import org.mochios.projects.lib.CollapsedColumnsStore
import org.mochios.projects.repository.ProjectsRepository
import org.mochios.projects.ui.project.ProjectViewModel
import org.mochios.projects.ui.`object`.ObjectDetailViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * An item's parent and link pickers read the board's list whatever its age:
 * the board keeps it current while it follows the frames, and drops it when
 * it cannot - a change it missed while covered, a refresh a pause cancelled,
 * the board gone. An item opened a minute after the board loaded used to
 * download the whole list again for its pickers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ObjectSiblingsTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var session: SessionManager
    private lateinit var repository: ProjectsRepository

    /** The server's end of the board's socket. */
    @Volatile private var live: WebSocket? = null

    /** How many times each path has been asked for. */
    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                counts.getOrPut(path) { AtomicInteger() }.incrementAndGet()
                return when (path) {
                    "/projects/p1/-/information" -> ok("""{"project": {"id": "p1", "name": "Board"}}""")
                    "/projects/p1/-/objects" -> ok("""{"objects": [{"id": "o1", "project": "p1"}, {"id": "o2", "project": "p1"}], "watched": []}""")
                    "/projects/p1/-/people" -> ok("""{"people": [{"id": "u1", "name": "Person"}]}""")
                    "/projects/p1/-/objects/o1" -> ok("""{"object": {"id": "o1", "project": "p1"}}""")
                    "/_/websocket" -> MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            live = webSocket
                        }

                        // Answer the client's close, or the server waits on it.
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

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun lists(): Int = counts["/projects/p1/-/objects"]?.get() ?: 0

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

    /** The board, loaded and following its frames. */
    private fun board(): ProjectViewModel {
        val model = ProjectViewModel(
            SavedStateHandle(mapOf("projectId" to "p1")),
            repository,
            socket,
            session,
            ActiveViewStore(context),
            CollapsedColumnsStore(context),
        )
        until { model.uiState.value.objects.isNotEmpty() && socket.isLive("p1") && live != null }
        settle()
        return model
    }

    /** Ages the board's held list past the minute the board's own reads keep it for. */
    private fun age() {
        val field = ProjectsRepository::class.java.getDeclaredField("objectsCache")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val cache = field.get(repository) as ConcurrentHashMap<String, Pair<Any, Long>>
        val held = cache.getValue("p1")
        cache["p1"] = held.first to (System.currentTimeMillis() - 120_000)
    }

    /** Opens an item over the board, and waits for its pickers' list. */
    private fun item() {
        val model = ObjectDetailViewModel(repository, session, socket)
        model.loadWithInitialObject("p1", "o1", null)
        until { model.uiState.value.siblingObjects.size == 2 }
        settle()
    }

    private fun pending(model: ProjectViewModel): Boolean {
        val field = ProjectViewModel::class.java.getDeclaredField("objectsPending")
        field.isAccessible = true
        return field.getBoolean(model)
    }

    @Test
    fun `an item opened long after the board loaded takes the board's list`() {
        val model = board()
        val before = lists()
        model.onScreenPaused()
        age()
        item()
        assertEquals("THE POINT: no second download of the whole list for the pickers", before, lists())
    }

    @Test
    fun `a change the covered board missed makes the next item read the list`() {
        val model = board()
        model.onScreenPaused()
        live!!.send("""{"type": "object/update"}""")
        settle()
        val before = lists()
        item()
        assertEquals("the list the board missed a change to is read again", before + 1, lists())
    }

    @Test
    fun `a refresh the pause cancelled makes the next item read the list`() {
        val model = board()
        live!!.send("""{"type": "object/update"}""")
        until { pending(model) }
        model.onScreenPaused()
        val before = lists()
        item()
        assertEquals("the list whose refresh was cancelled is read again", before + 1, lists())
    }

    @Test
    fun `a board that has gone leaves no list behind`() {
        val model = board()
        ViewModel::class.java.getDeclaredMethod("onCleared").apply { isAccessible = true }.invoke(model)
        val before = lists()
        item()
        assertEquals("nothing follows the frames for a list once its board has gone", before + 1, lists())
    }
}
