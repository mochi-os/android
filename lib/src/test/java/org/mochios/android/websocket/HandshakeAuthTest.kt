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
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Whichever way a caller names its credential - an explicit token, or an app
 * the socket mints for - it must reach the handshake, in the query the server
 * authorises from and in the header for anything that prefers it.
 */
class HandshakeAuthTest {
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket

    private val session = object : SocketSession {
        override suspend fun serverUrl(): String = url()

        override suspend fun token(app: String): String? = "minted-$app"

        override suspend fun invalidate(app: String) {
        }
    }

    @Before
    fun start() {
        server = MockWebServer()
        repeat(4) { server.enqueue(MockResponse().setResponseCode(503)) }
        server.start()
        socket = MochiWebSocket(OkHttpClient(), Gson(), session)
    }

    @After
    fun stop() {
        socket.disconnectAll()
        server.shutdown()
    }

    private fun url() = server.url("/").toString().trimEnd('/')

    @Test
    fun `an app name is minted into the handshake header`() {
        socket.subscribe(url(), "fp", app = "chat") {}
        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull("handshake arrived", request)
        assertEquals("/_/websocket?key=fp&token=minted-chat", request!!.path)
        assertEquals("Bearer minted-chat", request.getHeader("Authorization"))
    }

    @Test
    fun `an explicit token is sent as given`() {
        socket.subscribe(url(), "fp", token = "raw-token") {}
        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(request)
        assertEquals("/_/websocket?key=fp&token=raw-token", request!!.path)
        assertEquals("Bearer raw-token", request.getHeader("Authorization"))
    }

    @Test
    fun `a stream mints its app token too`() {
        socket.openStream("game-key", app = "chess")
        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull("stream handshake arrived", request)
        assertEquals("/_/websocket?key=game-key&token=minted-chess", request!!.path)
        assertEquals("Bearer minted-chess", request.getHeader("Authorization"))
    }
}
