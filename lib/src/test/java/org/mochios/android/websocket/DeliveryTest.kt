// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.websocket

import android.os.Looper
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Typed events reach their subscribers on the main thread, where every other
 * change to a screen's state is made, in the order the socket read them; a
 * subscription dropped while its delivery waited hears nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeliveryTest {
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket

    /** The server's end of the socket, once it is open. */
    @Volatile private var peer: WebSocket? = null

    @Before
    fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        peer = webSocket
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, reason)
                    }
                })
        }
        server.start()
        // The app's own socket, as Hilt makes it.
        socket = MochiWebSocket(OkHttpClient(), Gson(), object : SocketSession {
            override suspend fun serverUrl(): String = url()

            override suspend fun token(app: String): String? = null

            override suspend fun invalidate(app: String) {
            }
        })
    }

    @After
    fun stop() {
        socket.disconnectAll()
        server.shutdown()
    }

    private fun url() = server.url("/").toString().trimEnd('/')

    /** Runs the main looper until [done]. */
    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    @Test
    fun `typed events reach their subscribers on the main thread, in the order they came`() {
        val bodies = ConcurrentLinkedQueue<String>()
        val threads = ConcurrentLinkedQueue<Thread>()
        socket.subscribe(url(), "fp", "tok") { event ->
            threads.add(Thread.currentThread())
            event.body?.let(bodies::add)
        }
        until { peer != null && socket.isLive("fp") }
        repeat(20) { index -> peer!!.send("""{"body": "$index"}""") }
        until { bodies.size == 20 }
        assertEquals((0 until 20).map { index -> index.toString() }, bodies.toList())
        assertTrue("every event on the main thread", threads.all { thread -> thread === Looper.getMainLooper().thread })
    }

    @Test
    fun `a subscription dropped while its delivery waits hears nothing`() {
        val dropped = AtomicInteger()
        val kept = ConcurrentLinkedQueue<String>()
        val gone = socket.subscribe(url(), "fp", "tok") { dropped.incrementAndGet() }
        socket.subscribe(url(), "fp", "tok") { event -> event.body?.let(kept::add) }
        until { peer != null && socket.isLive("fp") }
        peer!!.send("""{"body": "late"}""")
        // Read, and waiting on the main thread's queue.
        val deadline = System.currentTimeMillis() + 5_000
        while (shadowOf(Looper.getMainLooper()).isIdle) {
            check(System.currentTimeMillis() < deadline) { "never delivered" }
            Thread.sleep(10)
        }
        socket.unsubscribe(gone)
        until { kept.isNotEmpty() }
        assertEquals(listOf("late"), kept.toList())
        assertEquals(0, dropped.get())
    }
}
