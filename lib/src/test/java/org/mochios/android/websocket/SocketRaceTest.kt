// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.websocket

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.ByteString
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * The socket's state per key holds together when its callers and its own
 * callbacks run at once: a subscription joining a key as its last one leaves
 * is kept, a socket closed in the background as it opens leaves its reopen for
 * the next one, and a second attempt to open a key that is already up leaves
 * it reported up.
 */
class SocketRaceTest {
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket

    /** Sockets the server has upgraded. */
    private val opened = AtomicInteger()

    /** The server's end of the latest socket. */
    @Volatile private var live: WebSocket? = null

    /** Holds the client's hearing of an open until released. */
    @Volatile private var pause: CountDownLatch? = null

    /** Holds a token being minted until released. */
    @Volatile private var minting: CountDownLatch? = null

    private val session = object : SocketSession {
        override suspend fun serverUrl(): String = url()

        override suspend fun token(app: String): String? {
            minting?.await(10, TimeUnit.SECONDS)
            return "minted"
        }

        override suspend fun invalidate(app: String) {
        }
    }

    /** A client whose sockets are heard opening only once [pause] lets them. */
    private val client = object : OkHttpClient() {
        override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket =
            super.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    pause?.await(10, TimeUnit.SECONDS)
                    listener.onOpen(webSocket, response)
                }

                override fun onMessage(webSocket: WebSocket, text: String) = listener.onMessage(webSocket, text)

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) = listener.onMessage(webSocket, bytes)

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) =
                    listener.onClosing(webSocket, code, reason)

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) =
                    listener.onClosed(webSocket, code, reason)

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
                    listener.onFailure(webSocket, t, response)
            })
    }

    @Before
    fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        live = webSocket
                        opened.incrementAndGet()
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, reason)
                    }
                })
        }
        server.start()
        socket = MochiWebSocket(client, Gson(), session)
    }

    @After
    fun stop() {
        pause?.countDown()
        minting?.countDown()
        socket.disconnectAll()
        server.shutdown()
    }

    private fun url() = server.url("/").toString().trimEnd('/')

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(20)
        }
    }

    @Test
    fun `a subscription joining a key as its last one leaves is never lost`() {
        // Nothing connects in the background, so the race is all there is.
        socket.background()
        val pool = Executors.newFixedThreadPool(2)
        try {
            repeat(3000) { round ->
                val first = socket.subscribe("http://localhost:1", "k", "tok") {}
                val barrier = CyclicBarrier(2)
                val leaving = pool.submit { barrier.await(); socket.unsubscribe(first) }
                val joining = pool.submit<String> { barrier.await(); socket.subscribe("http://localhost:1", "k", "tok") {} }
                leaving.get()
                val second = joining.get()
                assertTrue("round $round: the joining subscription is held", socket.holds(second))
                socket.unsubscribe(second)
            }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `a socket closed in the background as it opens leaves its reopen for the next one`() {
        val types = ConcurrentLinkedQueue<String?>()
        socket.subscribe(url(), "fp", "tok") { event -> types.add(event.type) }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        until { opened.get() == 1 && socket.isLive("fp") }
        // The socket drops; its reconnect opens on the server, and is heard
        // opening on the phone only after the app has gone to the background.
        pause = CountDownLatch(1)
        live!!.close(1001, "Going away")
        assertNotNull("reconnected", server.takeRequest(3, TimeUnit.SECONDS))
        until { opened.get() == 2 }
        socket.background()
        pause!!.countDown()
        Thread.sleep(500)
        types.clear()
        socket.foreground()
        until { REOPEN in types }
    }

    @Test
    fun `a second open of a key whose socket is up leaves it reported up`() {
        minting = CountDownLatch(1)
        socket.subscribe(url(), "fp", app = "chat") {}
        socket.subscribe(url(), "fp", token = "given") {}
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        until { socket.isLive("fp") }
        // The first subscription's token comes in after the socket is up.
        minting!!.countDown()
        Thread.sleep(500)
        assertTrue("still reported up", socket.isLive("fp"))
    }

    @Test
    fun `a stream closed just as it starts leaves nothing running`() {
        val stream = AtomicReference<MochiWebSocket.Stream?>()
        val closed = CountDownLatch(1)
        socket.stall = {
            Thread {
                while (stream.get() == null) Thread.sleep(5)
                stream.get()!!.close()
                closed.countDown()
            }.start()
            // Time for the close to land, were it not held off until the
            // mirror has started.
            Thread.sleep(300)
        }
        stream.set(socket.openStream("game", app = "chess"))
        assertTrue(closed.await(5, TimeUnit.SECONDS))
        // Long enough for the start to have finished, the stall included.
        Thread.sleep(1000)
        assertFalse("no mirror left running", stream.get()!!.mirrored)
        assertTrue("nothing subscribed", socket.newest().isEmpty())
    }
}
