// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.websocket

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * A socket that drops while the app is in the background comes back only
 * through its backoff timer - up to five minutes out. reconnectNow(), called
 * on resume, must open a fresh handshake at once, and the timer it superseded
 * must then stand down rather than open a competing socket.
 *
 * The server refuses every upgrade (a plain 503), so each connect fails
 * straight into backoff and the handshakes it receives count the attempts.
 */
class ReconnectNowTest {
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket

    @Before
    fun start() {
        server = MockWebServer()
        repeat(8) { server.enqueue(MockResponse().setResponseCode(503)) }
        server.start()
        socket = MochiWebSocket(OkHttpClient(), Gson())
    }

    @After
    fun stop() {
        socket.disconnectAll()
        server.shutdown()
    }

    @Test
    fun `reconnectNow opens a handshake without waiting out the backoff`() {
        socket.subscribe(server.url("/").toString().trimEnd('/'), "fp", "tok") {}
        assertNotNull("first handshake", server.takeRequest(5, TimeUnit.SECONDS))
        // The first retry is 1s +-20% out; the second 2s. Wake it before either.
        assertNotNull("the 1s retry", server.takeRequest(3, TimeUnit.SECONDS))
        // takeRequest returns as the server sees the handshake, before the
        // client has taken the 503 and dropped the socket; give it a moment so
        // the key really is down (an in-flight socket is rightly left alone).
        Thread.sleep(400)
        // Now in the ~2s wait. reconnectNow must not wait for it.
        socket.reconnectNow()
        val woken = server.takeRequest(500, TimeUnit.MILLISECONDS)
        assertNotNull("reconnectNow handshake arrived within 500ms", woken)
        assertEquals("/_/websocket?key=fp", woken!!.path)
    }

    @Test
    fun `a superseded backoff timer does not open a second socket`() {
        socket.subscribe(server.url("/").toString().trimEnd('/'), "fp", "tok") {}
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
        Thread.sleep(400)
        // In the ~2s wait: wake, then unsubscribe so nothing else reconnects.
        socket.reconnectNow()
        assertNotNull(server.takeRequest(500, TimeUnit.MILLISECONDS))
        socket.disconnectAll()
        // The superseded ~2s timer fires around now; with no subscribers and a
        // newer generation it must not connect.
        assertNull("no handshake from the superseded timer", server.takeRequest(3, TimeUnit.SECONDS))
    }
}
