// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.`object`

import android.os.Looper
import com.google.gson.Gson
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.mochios.crm.api.CrmsApi
import org.mochios.crm.model.CrmObject
import org.mochios.crm.repository.CrmsRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A CRM object's sheet, as the web panel has it: a picked value, a new parent and
 * watching show on the tap, and go back with the error if the server refuses.
 * Typed text stays as typed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ObjectInstantTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var model: ObjectDetailViewModel

    /** Holds every write until released, so a test sees the sheet before the answer. */
    private val gate = CountDownLatch(1)

    /** Whether the server refuses the writes it is sent. */
    @Volatile private var refuse = false

    /** Each write the server has been sent: its path and body. */
    private val writes = ConcurrentLinkedQueue<Pair<String, String>>()

    private val failures = mutableListOf<MochiError>()
    private val jobs = mutableListOf<Job>()

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                return when {
                    path == "/crm/p1/-/objects/o1" -> ok("""{"object": {"id": "o1", "crm": "p1", "parent": "x1"}}""")
                    path == "/crm/p1/-/objects/o1/watchers" -> ok("""{"watchers": [], "watching": false}""")
                    path.startsWith("/crm/p1/-/objects/o1/values/") ||
                        path == "/crm/p1/-/objects/o1/update" ||
                        path.startsWith("/crm/p1/-/objects/o1/watchers/") -> {
                        writes.add(path to request.body.readUtf8())
                        gate.await(5, TimeUnit.SECONDS)
                        if (refuse) MockResponse().setResponseCode(500).setBody("""{"error": "Refused"}""") else ok("{}")
                    }
                    path == "/_/websocket" -> MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
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
            .baseUrl(server.url("/crm/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CrmsApi::class.java)
        val session = SessionManager(context)
        runBlocking { session.setServerUrl(base) }
        socket = MochiWebSocket(OkHttpClient(), Gson(), object : SocketSession {
            override suspend fun serverUrl(): String = base

            override suspend fun token(app: String): String? = "token"

            override suspend fun invalidate(app: String) {
            }
        })
        model = ObjectDetailViewModel(CrmsRepository(api, FileStore(context), context, session), session, socket)
        jobs += CoroutineScope(Dispatchers.Main).launch { model.saveFailed.collect { failures += it } }
        jobs += CoroutineScope(Dispatchers.Main).launch { model.actionFailed.collect { failures += it } }
        val start = CrmObject(id = "o1", crm = "p1", parent = "x1", values = mapOf("status" to "open", "title" to "Old"))
        model.loadWithInitialObject("p1", "o1", start)
        settle()
    }

    @After
    fun end() {
        gate.countDown()
        jobs.forEach { it.cancel() }
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

    private fun value(field: String) = model.uiState.value.obj?.values?.get(field)

    private fun refused() {
        refuse = true
        gate.countDown()
        until { failures.isNotEmpty() }
    }

    @Test
    fun `a picked value shows on the tap, and the server's goes back when refused`() {
        model.setValue("status", "done", "enumerated")
        assertEquals("done", value("status"))
        until { writes.any { it.first.endsWith("/values/status") } }
        refused()
        assertEquals("open", value("status"))
        assertEquals("Old", value("title"))
    }

    @Test
    fun `quick picks of one field save once, and a refusal puts back what the server had`() {
        model.setValue("status", "doing", "enumerated")
        model.setValue("status", "done", "enumerated")
        until { writes.any { it.first.endsWith("/values/status") } }
        settle()
        assertEquals(1, writes.count { it.first.endsWith("/values/status") })
        refused()
        assertEquals("open", value("status"))
    }

    @Test
    fun `an accepted pick stays`() {
        model.setValue("status", "done", "enumerated")
        gate.countDown()
        until { writes.any { it.first.endsWith("/values/status") } }
        settle()
        assertEquals("done", value("status"))
        assertTrue(failures.isEmpty())
    }

    @Test
    fun `typed text stays as typed when refused`() {
        model.setValue("title", "New")
        until { writes.any { it.first.endsWith("/values/title") } }
        refused()
        assertEquals("New", value("title"))
    }

    @Test
    fun `a new parent shows on the tap, and the old one comes back when refused`() {
        model.updateParent("x2")
        assertEquals("x2", model.uiState.value.obj?.parent)
        until { writes.any { it.first.endsWith("/update") } }
        refused()
        assertEquals("x1", model.uiState.value.obj?.parent)
    }

    @Test
    fun `watching flips on the tap, and back with the error when refused`() {
        until { writes.isEmpty() && !model.uiState.value.isWatching }
        model.toggleWatch()
        assertTrue(model.uiState.value.isWatching)
        until { writes.any { it.first.endsWith("/watchers/add") } }
        refused()
        assertEquals(false, model.uiState.value.isWatching)
    }
}
