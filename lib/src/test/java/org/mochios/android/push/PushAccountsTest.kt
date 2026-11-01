// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import com.google.gson.Gson
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
import org.mochios.android.model.WebSocketEvent
import org.mochios.android.websocket.Mint
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Every account on the device has a push channel of its own, authorised with
 * its own token, even when two share a server, and each hears only its own
 * pushes, once. When the signed-in account logs out, which drops the session's
 * sockets, the other accounts' channels stay open and go on delivering.
 */
class PushAccountsTest {
    private val first = Server()
    private val second = Server()
    private lateinit var socket: MochiWebSocket

    /**
     * A server: the sockets it holds by key, and by the credential each
     * authorised with, and the closes it was sent, by key.
     */
    private class Server {
        val server = MockWebServer()
        val live = ConcurrentHashMap<String, WebSocket>()
        val credentials = ConcurrentHashMap<String, WebSocket>()
        val closes = ConcurrentHashMap<String, AtomicInteger>()

        fun start() {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val key = request.requestUrl?.queryParameter("key").orEmpty()
                    val credential = request.getHeader("Authorization").orEmpty()
                    return MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            live[key] = webSocket
                            credentials[credential] = webSocket
                        }

                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            closes.getOrPut(key) { AtomicInteger() }.incrementAndGet()
                            webSocket.close(code, reason)
                        }
                    })
                }
            }
            server.start()
        }

        fun url() = server.url("/").toString().trimEnd('/')

        fun closed(key: String) = closes[key]?.get() ?: 0
    }

    @Before
    fun start() {
        first.start()
        second.start()
        socket = MochiWebSocket(OkHttpClient(), Gson(), object : SocketSession {
            override suspend fun serverUrl(): String = first.url()

            override suspend fun token(app: String): String? = "session"

            override suspend fun invalidate(app: String) {
            }
        })
    }

    @After
    fun stop() {
        first.server.shutdown()
        second.server.shutdown()
    }

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(20)
        }
    }

    @Test
    fun `the other account keeps its push when the signed-in one logs out`() {
        val pushes = ConcurrentLinkedQueue<String?>()
        socket.subscribePush(first.url(), "a1", "first", mint = { Mint.Later }, drain = {}) {}
        socket.subscribePush(second.url(), "a2", "second", mint = { Mint.Later }, drain = {}) { event: WebSocketEvent -> pushes.add(event.subId) }
        socket.subscribe(first.url(), "feed", app = "feeds") {}
        until {
            first.live.containsKey("unifiedpush") && first.live.containsKey("feed") &&
                second.live.containsKey("unifiedpush") && socket.isLive("feed")
        }
        Thread.sleep(300)

        // The signed-in account logs out.
        socket.disconnectAll()
        until { first.closed("feed") == 1 }
        Thread.sleep(1000)
        assertEquals("the other account's push channel stays open", 0, second.closed("unifiedpush"))

        second.live.getValue("unifiedpush").send("""{"sub_id": "s2", "payload": "{}"}""")
        until { "s2" in pushes }
    }

    @Test
    fun `two accounts on one server each hear their own pushes, once`() {
        val one = ConcurrentLinkedQueue<String?>()
        val two = ConcurrentLinkedQueue<String?>()
        socket.subscribePush(first.url(), "a1", "one", mint = { Mint.Later }, drain = {}) { event: WebSocketEvent -> one.add(event.subId) }
        socket.subscribePush(first.url(), "a2", "two", mint = { Mint.Later }, drain = {}) { event: WebSocketEvent -> two.add(event.subId) }
        until { first.credentials.containsKey("Bearer one") }
        // Time for a second socket, where there is one, to open on the client.
        Thread.sleep(500)

        first.credentials.getValue("Bearer one").send("""{"sub_id": "s1", "payload": "{}"}""")
        until { "s1" in one }
        Thread.sleep(300)
        assertEquals("the first account's push, once", listOf<String?>("s1"), one.toList())
        assertEquals("nothing of the first account's reaches the second", emptyList<String?>(), two.toList())

        until { first.credentials.containsKey("Bearer two") }
        first.credentials.getValue("Bearer two").send("""{"sub_id": "s2", "payload": "{}"}""")
        until { "s2" in two }
        Thread.sleep(300)
        assertEquals(listOf<String?>("s2"), two.toList())
        assertEquals(listOf<String?>("s1"), one.toList())
    }

    @Test
    fun `an account's queue is drained once its socket is open, and again each time it opens`() {
        val drains = ConcurrentLinkedQueue<Boolean>()
        socket.subscribePush(first.url(), "a1", "one", mint = { Mint.Later }, drain = { drains.add(socket.isLive("unifiedpush")) }) {}
        until { drains.count { live -> live } == 1 && first.live.containsKey("unifiedpush") }
        first.live.getValue("unifiedpush").close(1001, "Going away")
        until { drains.count { live -> live } == 2 }
    }

    @Test
    fun `an account's queue is drained even when its socket cannot open`() {
        val refusing = MockWebServer()
        refusing.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = MockResponse().setResponseCode(429)
        }
        refusing.start()
        try {
            val drains = AtomicInteger()
            socket.subscribePush(refusing.url("/").toString().trimEnd('/'), "a1", "one", mint = { Mint.Later }, drain = { drains.incrementAndGet() }) {}
            until { drains.get() >= 1 }
        } finally {
            refusing.shutdown()
        }
    }
}
