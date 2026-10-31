// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.crm

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
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
import org.junit.Assert.assertNull
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
import org.mochios.crm.lib.ActiveViewStore
import org.mochios.crm.lib.CollapsedColumnsStore
import org.mochios.crm.repository.CrmsRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A CRM's information, items and people are asked for together - as it
 * opens, on a pull and on a silent refresh - and the information still
 * decides the page's error, whatever the items said.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CrmLoadTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var session: SessionManager
    private lateinit var repository: CrmsRepository

    /** Counted down as the items and the people are asked for. */
    @Volatile private var asked = CountDownLatch(2)

    /** Whether the items and people had both been asked for while the information was unanswered. */
    @Volatile private var overlapped = false

    /** How many times the information has been answered. */
    private val informations = AtomicInteger()

    /** The status the information answers with, after the items are asked for. */
    @Volatile private var refusal: Int? = null

    /** The status the items answer with; null answers them. */
    @Volatile private var failure: Int? = null

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                return when (path) {
                    "/crm/c1/-/information" -> {
                        overlapped = asked.await(2, TimeUnit.SECONDS)
                        val status = refusal
                        val answer = if (status != null) {
                            Thread.sleep(300)
                            MockResponse().setResponseCode(status).setBody("""{"error": "refused"}""")
                        } else {
                            ok("""{"crm": {"id": "c1", "name": "CRM"}}""")
                        }
                        informations.incrementAndGet()
                        answer
                    }
                    "/crm/c1/-/objects" -> {
                        asked.countDown()
                        val status = failure
                        if (status != null) MockResponse().setResponseCode(status).setBody("""{"error": "down"}""")
                        else ok("""{"objects": [{"id": "o1", "crm": "c1"}], "watched": []}""")
                    }
                    "/crm/c1/-/people" -> {
                        asked.countDown()
                        ok("""{"people": [{"id": "u1", "name": "Person"}]}""")
                    }
                    "/_/websocket" -> MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) = Unit

                        // Answer the client's close, or the server waits on it.
                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            webSocket.close(code, reason)
                        }
                    })
                    else -> MockResponse().setResponseCode(404)
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
        session = SessionManager(context)
        runBlocking { session.setServerUrl(base) }
        socket = MochiWebSocket(OkHttpClient(), Gson(), object : SocketSession {
            override suspend fun serverUrl(): String = base

            override suspend fun token(app: String): String? = "token"

            override suspend fun invalidate(app: String) {
            }
        })
        repository = CrmsRepository(api, FileStore(context), context)
    }

    @After
    fun end() {
        asked.countDown()
        asked.countDown()
        socket.disconnectAll()
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    /** The CRM's screen as it opens. */
    private fun open() = CrmViewModel(
        SavedStateHandle(mapOf("crmId" to "c1")),
        repository,
        socket,
        session,
        ActiveViewStore(context),
        CollapsedColumnsStore(context),
    )

    /** Runs the main looper the network's answers resume on until [done]. */
    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    /** Opens the CRM and waits for it. */
    private fun loaded(): CrmViewModel {
        val model = open()
        until { model.uiState.value.objects.isNotEmpty() && model.uiState.value.people.isNotEmpty() && !model.uiState.value.isLoading }
        return model
    }

    @Test
    fun `information, items and people are asked for together as the CRM opens`() {
        loaded()
        assertTrue(overlapped)
    }

    @Test
    fun `information, items and people are asked for together on a pull`() {
        val model = loaded()
        asked = CountDownLatch(2)
        overlapped = false
        model.refresh()
        until { informations.get() == 2 && !model.uiState.value.isRefreshing }
        assertTrue(overlapped)
    }

    @Test
    fun `information, items and people are asked for together on a silent refresh`() {
        loaded()
        asked = CountDownLatch(2)
        overlapped = false
        // Opened again while the first answers are held, the CRM shows
        // them and refreshes silently behind.
        open()
        until { informations.get() == 2 }
        assertTrue(overlapped)
    }

    @Test
    fun `a refusal of the information is the page error, and the items asked beside it are not shown`() {
        refusal = 403
        val model = open()
        until { model.uiState.value.error != null && !model.uiState.value.isLoading }
        assertTrue(model.uiState.value.error is MochiError.ForbiddenError)
        assertEquals(emptyList<Any>(), model.uiState.value.objects)
        assertNull(model.uiState.value.crmDetails)
    }

    @Test
    fun `a refusal of the information still wins when the items fail first`() {
        refusal = 404
        failure = 500
        val model = open()
        until { model.uiState.value.error != null && !model.uiState.value.isLoading }
        assertTrue(model.uiState.value.error is MochiError.NotFoundError)
    }
}
