// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.websocket

import com.google.gson.Gson
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A handshake core refuses with 401 is answered with a fresh token, from the
 * session for a key with an app, or from the key's own minter for one with
 * none, rather than with no credential at all. A token minted for a session
 * that has since logged out is not used.
 */
class SocketAuthTest {
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket

    /** The credentials core refuses. */
    private val refused = setOf("Bearer old", "Bearer stale")

    /** How many times the session has been asked for a token. */
    private val asked = AtomicInteger()

    /** Whether the session's token has been given up. */
    @Volatile private var invalidated = false

    /** Holds each of the session's first two mints until released. */
    private val gates = listOf(CountDownLatch(0), CountDownLatch(0)).toMutableList()

    private val session = object : SocketSession {
        override suspend fun serverUrl(): String = url()

        override suspend fun token(app: String): String? {
            val call = asked.getAndIncrement()
            gates.getOrNull(call)?.await(10, TimeUnit.SECONDS)
            return when {
                app == "logout" -> if (call == 0) "old" else "new"
                invalidated -> "fresh"
                else -> "stale"
            }
        }

        override suspend fun invalidate(app: String) {
            invalidated = true
        }
    }

    @Before
    fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.getHeader("Authorization") in refused) return MockResponse().setResponseCode(401)
                return MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) = Unit

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, reason)
                    }
                })
            }
        }
        server.start()
        socket = MochiWebSocket(OkHttpClient(), Gson(), session)
    }

    @After
    fun stop() {
        gates.forEach { gate -> gate.countDown() }
        socket.disconnectAll()
        server.shutdown()
    }

    private fun url() = server.url("/").toString().trimEnd('/')

    @Test
    fun `a key with no app mints its own fresh token after a refusal`() {
        socket.subscribe(url(), "unifiedpush", token = "old", account = "a1", mint = { Mint.Fresh("new") }) {}
        assertEquals("Bearer old", server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("Authorization"))
        assertEquals("Bearer new", server.takeRequest(3, TimeUnit.SECONDS)!!.getHeader("Authorization"))
    }

    @Test
    fun `a key with an app mints a fresh token from the session after a refusal`() {
        socket.subscribe(url(), "notifications", app = "notifications") {}
        assertEquals("Bearer stale", server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("Authorization"))
        assertEquals("Bearer fresh", server.takeRequest(3, TimeUnit.SECONDS)!!.getHeader("Authorization"))
    }

    @Test
    fun `a token minted for a session that has logged out is not used`() {
        gates[0] = CountDownLatch(1)
        gates[1] = CountDownLatch(1)
        socket.subscribe(url(), "fp", app = "logout") {}
        Thread.sleep(200)
        socket.disconnectAll()
        // The next account subscribes the same key; its token is minted after
        // the old account's comes in.
        socket.subscribe(url(), "fp", app = "logout") {}
        Thread.sleep(200)
        gates[0].countDown()
        Thread.sleep(300)
        gates[1].countDown()
        val handshake = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(handshake)
        assertEquals("Bearer new", handshake!!.getHeader("Authorization"))
        assertNull("no handshake with the old account's token", server.takeRequest(500, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `a key whose minter cannot be reached opens nothing without a credential`() {
        val answers = ArrayDeque(listOf<Mint>(Mint.Later, Mint.Fresh("new")))
        socket.subscribe(url(), "unifiedpush", token = "old", account = "a1", mint = { synchronized(answers) { answers.removeFirst() } }) {}
        assertEquals("Bearer old", server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("Authorization"))
        // Refused; the minter cannot be reached; the ladder tries it again.
        assertEquals("Bearer new", server.takeRequest(6, TimeUnit.SECONDS)!!.getHeader("Authorization"))
    }

    @Test
    fun `a key whose session is gone waits to be subscribed afresh`() {
        val mints = AtomicInteger()
        socket.subscribe(url(), "unifiedpush", token = "old", account = "a1", mint = { mints.incrementAndGet(); Mint.Gone }) {}
        assertEquals("Bearer old", server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("Authorization"))
        assertNull("nothing more", server.takeRequest(3500, TimeUnit.MILLISECONDS))
        assertEquals("asked once", 1, mints.get())
    }

    @Test
    fun `a device account's socket carries no cookie, and the session's does`() {
        val signedIn = object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> =
                listOf(Cookie.Builder().domain(url.host).path("/").name("session").value("signed-in").build())
        }
        val jarred = MochiWebSocket(OkHttpClient.Builder().cookieJar(signedIn).build(), Gson(), session)
        try {
            val cookies = ConcurrentLinkedQueue<String>()
            jarred.subscribe(url(), "unifiedpush", token = "fresh", account = "a2") {}
            val account = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertNull("the account's handshake", account.getHeader("Cookie"))
            jarred.subscribe(url(), "feeds", token = "fresh") {}
            val own = server.takeRequest(5, TimeUnit.SECONDS)!!
            own.getHeader("Cookie")?.let { cookie -> cookies.add(cookie) }
            assertEquals("the session's handshake", listOf("session=signed-in"), cookies.toList())
        } finally {
            jarred.disconnectAll()
        }
    }

    @Test
    fun `a device account's socket with no token of its own opens none`() {
        socket.subscribe(url(), "unifiedpush", account = "a1") {}
        assertNull("no handshake without a credential", server.takeRequest(1500, TimeUnit.MILLISECONDS))
    }
}
