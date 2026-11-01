// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.websocket

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Core caps a user's sockets at 32 across every app, tab and device, and
 * refuses the next handshake with 429. The app's sockets close while it is in
 * the background and open again as it returns, all but the ones the push and
 * notification paths need there. A key refused at the cap keeps to its backoff
 * ladder and is tried again as soon as one of this process's own sockets
 * closes. A socket that opens again after being down tells its subscribers so,
 * once it is open, since nothing sent meanwhile reached them.
 */
class BackgroundTest {
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket

    /** How many handshakes to refuse with 429 before upgrading. */
    private val refusals = AtomicInteger()

    /** Holds an upgrade until released. */
    @Volatile private var hold: CountDownLatch? = null

    /** Sockets the server has upgraded. */
    private val opened = AtomicInteger()

    /** Closes the client has sent. */
    private val closed = AtomicInteger()

    /** A frame the server sends as each socket opens; null sends none. */
    @Volatile private var greeting: String? = null

    /** A frame the server sends after the client's close, before its own; null sends none. */
    @Volatile private var parting: String? = null

    /** The server's end of the latest socket. */
    @Volatile private var live: WebSocket? = null

    private val session = object : SocketSession {
        override suspend fun serverUrl(): String = url()

        override suspend fun token(app: String): String? = null

        override suspend fun invalidate(app: String) {
        }

        override fun identity(): String = signedIn
    }

    /** The account signed in. */
    @Volatile private var signedIn = "me"

    @Before
    fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (refusals.getAndDecrement() > 0) return MockResponse().setResponseCode(429)
                hold?.await(10, TimeUnit.SECONDS)
                return MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        live = webSocket
                        opened.incrementAndGet()
                        greeting?.let { text -> webSocket.send(text) }
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        closed.incrementAndGet()
                        parting?.let { text -> webSocket.send(text) }
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
        hold?.countDown()
        socket.disconnectAll()
        server.shutdown()
    }

    private fun url() = server.url("/").toString().trimEnd('/')

    /** Waits up to five seconds for [done]. */
    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(20)
        }
    }

    @Test
    fun `the background closes a socket and the foreground opens it again`() {
        socket.subscribe(url(), "fp", "tok") {}
        assertNotNull("handshake", server.takeRequest(5, TimeUnit.SECONDS))
        until { opened.get() == 1 && socket.isLive("fp") }
        socket.background()
        until { closed.get() == 1 }
        // A dropped socket would be back within about a second.
        assertNull("nothing reopens in the background", server.takeRequest(2500, TimeUnit.MILLISECONDS))
        socket.foreground()
        assertNotNull("reopened", server.takeRequest(2, TimeUnit.SECONDS))
        until { opened.get() == 2 }
    }

    @Test
    fun `a key subscribed in the background waits for the foreground, and hears it open as a reopen`() {
        val types = ConcurrentLinkedQueue<String?>()
        socket.background()
        socket.subscribe(url(), "fp", "tok") { event -> types.add(event.type) }
        assertNull("no handshake in the background", server.takeRequest(1500, TimeUnit.MILLISECONDS))
        socket.foreground()
        assertNotNull("handshake on return", server.takeRequest(2, TimeUnit.SECONDS))
        until { REOPEN in types }
    }

    @Test
    fun `a socket subscribed to stay open in the background stays open`() {
        socket.subscribe(url(), "push", "tok", background = true) {}
        socket.subscribe(url(), "screen", "tok") {}
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        until { opened.get() == 2 && socket.isLive("push") && socket.isLive("screen") }
        socket.background()
        until { closed.get() == 1 }
        Thread.sleep(1000)
        assertEquals("only the screen's socket closed", 1, closed.get())
        assertTrue("the push socket is live", socket.isLive("push"))
    }

    @Test
    fun `a handshake refused at the cap keeps to the ladder, and the socket it opens tells of the gap`() {
        refusals.set(1)
        val types = ConcurrentLinkedQueue<String?>()
        socket.subscribe(url(), "fp", "tok") { event -> types.add(event.type) }
        assertNotNull("refused handshake", server.takeRequest(5, TimeUnit.SECONDS))
        // The backoff's first retry is 1s +-20% out.
        assertNotNull("retried on the ladder", server.takeRequest(3, TimeUnit.SECONDS))
        until { opened.get() == 1 }
        until { REOPEN in types }
    }

    @Test
    fun `a resume retries a refused key at once`() {
        refusals.set(1)
        socket.subscribe(url(), "fp", "tok") {}
        assertNotNull("refused handshake", server.takeRequest(5, TimeUnit.SECONDS))
        // Let the client take the 429; the ladder's retry is 800ms out at the soonest.
        Thread.sleep(400)
        socket.reconnectNow()
        assertNotNull("retried on resume", server.takeRequest(300, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `a refused key is tried again as soon as one of our own sockets closes`() {
        val held = socket.subscribe(url(), "a", "tok") {}
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        until { opened.get() == 1 && socket.isLive("a") }
        refusals.set(1)
        socket.subscribe(url(), "b", "tok") {}
        assertNotNull("refused handshake", server.takeRequest(5, TimeUnit.SECONDS))
        Thread.sleep(250)
        socket.unsubscribe(held)
        // The ladder's retry is 800ms after the refusal at the soonest.
        val retried = server.takeRequest(450, TimeUnit.MILLISECONDS)
        assertNotNull("retried as a place came free", retried)
        assertEquals("/_/websocket?key=b", retried!!.path)
    }

    @Test
    fun `a return to the foreground asks for the newest subscription first`() {
        socket.background()
        socket.subscribe(url(), "a", "tok") {}
        socket.subscribe(url(), "b", "tok") {}
        socket.subscribe(url(), "c", "tok") {}
        assertEquals(listOf("c", "b", "a"), socket.newest().map { key -> key.substringAfterLast("::") })
    }

    @Test
    fun `after the background a subscriber hears the socket reopen, once it is open`() {
        val types = ConcurrentLinkedQueue<String?>()
        val live = ConcurrentLinkedQueue<Boolean>()
        socket.subscribe(url(), "fp", "tok") { event ->
            types.add(event.type)
            if (event.type == REOPEN) live.add(socket.isLive("fp"))
        }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        until { opened.get() == 1 && socket.isLive("fp") }
        Thread.sleep(300)
        assertFalse("a first open is no reopen", REOPEN in types)
        socket.background()
        until { closed.get() == 1 }
        socket.foreground()
        until { REOPEN in types }
        assertEquals("open as it was told", listOf(true), live.toList())
    }

    @Test
    fun `a dropped socket's subscribers hear it reopen`() {
        val types = ConcurrentLinkedQueue<String?>()
        socket.subscribe(url(), "fp", "tok") { event -> types.add(event.type) }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        until { opened.get() == 1 }
        live!!.close(1001, "Going away")
        assertNotNull("reconnected on the ladder", server.takeRequest(3, TimeUnit.SECONDS))
        until { REOPEN in types }
    }

    @Test
    fun `a stream hears the reopen too`() {
        val stream = socket.openStream("game", app = "chess")
        val types = ConcurrentLinkedQueue<String>()
        val collector = CoroutineScope(Dispatchers.IO).launch {
            stream.events.collect { event -> types.add(event.type) }
        }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        until { opened.get() == 1 && socket.isLive("game") }
        socket.background()
        until { closed.get() == 1 }
        socket.foreground()
        until { REOPEN in types }
        collector.cancel()
        stream.close()
    }

    @Test
    fun `a socket closed while still connecting is cancelled, and neither reports itself open nor delivers`() {
        hold = CountDownLatch(1)
        greeting = """{"type": "post/create"}"""
        val connects = AtomicInteger()
        val events = AtomicInteger()
        socket.subscribeConnected(url(), "fp") { connects.incrementAndGet() }
        socket.subscribe(url(), "fp", "tok") { events.incrementAndGet() }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        socket.background()
        hold!!.countDown()
        Thread.sleep(1500)
        assertEquals("no close sent: the upgrade was cancelled", 0, closed.get())
        assertEquals("no connect listener", 0, connects.get())
        assertEquals("no frame delivered", 0, events.get())
        assertFalse(socket.isLive("fp"))
    }

    @Test
    fun `frames still arriving on a socket closed on purpose are not delivered`() {
        parting = """{"type": "post/create"}"""
        val types = ConcurrentLinkedQueue<String?>()
        socket.subscribe(url(), "fp", "tok") { event -> types.add(event.type) }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        until { opened.get() == 1 && socket.isLive("fp") }
        // Back at once: the closing socket's last frame lands while the app is
        // in the foreground again, beside the new socket.
        socket.background()
        socket.foreground()
        until { closed.get() == 1 }
        Thread.sleep(500)
        assertFalse("the closing socket's frame is not delivered", "post/create" in types)
    }

    @Test
    fun `a socket closed on purpose is let go once nothing else holds it`() {
        val reference = retireOne()
        val deadline = System.currentTimeMillis() + 5_000
        while (reference.get() != null && System.currentTimeMillis() < deadline) {
            System.gc()
            Thread.sleep(50)
        }
        assertNull("collected", reference.get())
        assertEquals(0, socket.retired.size)
    }

    /** Retires a socket whose callbacks have all run, and keeps no hold on it. */
    private fun retireOne(): WeakReference<WebSocket> {
        val gone = object : WebSocket {
            override fun request(): Request = Request.Builder().url(url()).build()

            override fun queueSize(): Long = 0

            override fun send(text: String): Boolean = false

            override fun send(bytes: ByteString): Boolean = false

            override fun close(code: Int, reason: String?): Boolean = false

            override fun cancel() {
            }
        }
        socket.retire(gone, "Test")
        assertEquals(1, socket.retired.size)
        return WeakReference(gone)
    }

    @Test
    fun `a place freed on one server retries nothing refused on another`() {
        val other = MockWebServer()
        other.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = MockResponse().setResponseCode(429)
        }
        other.start()
        try {
            val held = socket.subscribe(url(), "a", "tok") {}
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            until { opened.get() == 1 && socket.isLive("a") }
            socket.subscribe(other.url("/").toString().trimEnd('/'), "b", "tok") {}
            assertNotNull("refused", other.takeRequest(5, TimeUnit.SECONDS))
            Thread.sleep(250)
            socket.unsubscribe(held)
            until { closed.get() == 1 }
            // The ladder's retry is 800ms after the refusal at the soonest.
            assertNull("nothing retried on the other server", other.takeRequest(300, TimeUnit.MILLISECONDS))
        } finally {
            socket.disconnectAll()
            other.shutdown()
        }
    }

    @Test
    fun `a retry a freed place starts keeps the ladder's rung`() {
        val held = socket.subscribe(url(), "a", "tok") {}
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        until { opened.get() == 1 && socket.isLive("a") }
        refusals.set(2)
        socket.subscribe(url(), "b", "tok") {}
        assertNotNull("refused", server.takeRequest(5, TimeUnit.SECONDS))
        Thread.sleep(250)
        socket.unsubscribe(held)
        val freed = server.takeRequest(450, TimeUnit.MILLISECONDS)
        assertNotNull("retried as a place came free, and refused again", freed)
        assertEquals("/_/websocket?key=b", freed!!.path)
        val start = System.nanoTime()
        assertNotNull("the ladder's next retry", server.takeRequest(4, TimeUnit.SECONDS))
        val waited = (System.nanoTime() - start) / 1_000_000
        assertTrue("still on the first rung, 1s +-20%, not the second: $waited ms", waited < 1400)
    }

    @Test
    fun `a key stays open in the background only while a subscription that asked for it remains`() {
        val kept = socket.subscribe(url(), "fp", "tok", background = true) {}
        socket.subscribe(url(), "fp", "tok") {}
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        until { opened.get() == 1 && socket.isLive("fp") }
        socket.background()
        Thread.sleep(500)
        assertEquals("kept open", 0, closed.get())
        socket.unsubscribe(kept)
        until { closed.get() == 1 }
    }

    @Test
    fun `in the background only the subscription keeping the socket open hears it, and the other is told on return`() {
        val kept = ConcurrentLinkedQueue<String?>()
        val other = ConcurrentLinkedQueue<String?>()
        socket.subscribe(url(), "fp", "tok", background = true) { event -> kept.add(event.type) }
        socket.subscribe(url(), "fp", "tok") { event -> other.add(event.type) }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        until { opened.get() == 1 && socket.isLive("fp") }
        socket.background()
        live!!.send("""{"type": "post/create"}""")
        until { "post/create" in kept }
        Thread.sleep(300)
        assertFalse("not heard in the background", "post/create" in other)
        socket.foreground()
        until { REOPEN in other }
        Thread.sleep(300)
        assertFalse("the one that heard it all is not told", REOPEN in kept)
    }

    @Test
    fun `reconnecting while subscriptions come and go never throws`() {
        // Nothing connects in the background; the order of the keys is all
        // there is to it.
        socket.background()
        val keys = (1..40).map { n -> "k$n" }
        keys.forEach { key -> socket.subscribe("http://localhost:1", key, "tok") {} }
        val stop = AtomicBoolean(false)
        val thrown = AtomicReference<Throwable?>()
        val pool = Executors.newFixedThreadPool(3)
        try {
            repeat(3) {
                pool.submit {
                    while (!stop.get()) {
                        val id = socket.subscribe("http://localhost:1", keys.random(), "tok") {}
                        socket.unsubscribe(id)
                    }
                }
            }
            repeat(3000) {
                try {
                    socket.reconnectNow()
                } catch (e: Throwable) {
                    thrown.compareAndSet(null, e)
                }
            }
        } finally {
            stop.set(true)
            pool.shutdown()
            pool.awaitTermination(5, TimeUnit.SECONDS)
        }
        assertNull("reconnecting threw", thrown.get())
    }

    @Test
    fun `a freed place retries one refused key, the newest`() {
        val held = socket.subscribe(url(), "a", "tok") {}
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        until { opened.get() == 1 && socket.isLive("a") }
        refusals.set(3)
        for (key in listOf("b", "c", "d")) {
            socket.subscribe(url(), key, "tok") {}
            assertNotNull("refused", server.takeRequest(5, TimeUnit.SECONDS))
        }
        Thread.sleep(250)
        socket.unsubscribe(held)
        // The ladder's retries are 800ms after each refusal at the soonest.
        val retried = server.takeRequest(450, TimeUnit.MILLISECONDS)
        assertNotNull("one retried as the place came free", retried)
        assertEquals("/_/websocket?key=d", retried!!.path)
        assertNull("and only one", server.takeRequest(100, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `a superseded backoff timer leaves the queue`() {
        refusals.set(1_000)
        val keys = (1..10).map { n -> "k$n" }
        keys.forEach { key -> socket.subscribe(url(), key, "tok") {} }
        until { socket.waiting() == keys.size }
        repeat(5) {
            socket.reconnectNow()
            Thread.sleep(200)
        }
        until { server.requestCount >= keys.size * 6 }
        Thread.sleep(300)
        assertEquals("one timer a key", keys.size, socket.waiting())
    }

    @Test
    fun `a place the session's socket frees retries the signed-in account's refused push`() {
        val held = socket.subscribe(url(), "a", "tok") {}
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        until { opened.get() == 1 && socket.isLive("a") }
        refusals.set(1)
        socket.subscribe(url(), "unifiedpush", token = "tok", background = true, account = "me") {}
        assertNotNull("refused", server.takeRequest(5, TimeUnit.SECONDS))
        Thread.sleep(250)
        socket.unsubscribe(held)
        val retried = server.takeRequest(450, TimeUnit.MILLISECONDS)
        assertNotNull("retried as the place came free", retried)
        assertEquals("/_/websocket?key=unifiedpush", retried!!.path)
    }

    @Test
    fun `a place freed goes to the user the socket opened as`() {
        val held = socket.subscribe(url(), "a", "tok") {}
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        until { opened.get() == 1 && socket.isLive("a") }
        refusals.set(2)
        // The opening account's push channel, refused.
        socket.subscribe(url(), "unifiedpush", token = "tok", background = true, account = "me") {}
        assertNotNull("refused", server.takeRequest(5, TimeUnit.SECONDS))
        // Another account signs in, and its socket is refused too.
        signedIn = "other"
        socket.subscribe(url(), "y", "tok") {}
        assertNotNull("refused", server.takeRequest(5, TimeUnit.SECONDS))
        Thread.sleep(250)
        socket.unsubscribe(held)
        val retried = server.takeRequest(450, TimeUnit.MILLISECONDS)
        assertNotNull("retried as the place came free", retried)
        assertEquals("the place was the opening account's", "/_/websocket?key=unifiedpush", retried!!.path)
    }

    @Test
    fun `a reopen in the background reaches the resident subscription only, and the other once on return`() {
        val kept = ConcurrentLinkedQueue<String?>()
        val other = ConcurrentLinkedQueue<String?>()
        socket.subscribe(url(), "fp", "tok", background = true) { event -> kept.add(event.type) }
        socket.subscribe(url(), "fp", "tok") { event -> other.add(event.type) }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        until { opened.get() == 1 && socket.isLive("fp") }
        socket.background()
        live!!.close(1001, "Going away")
        assertNotNull("reconnected in the background", server.takeRequest(3, TimeUnit.SECONDS))
        until { REOPEN in kept }
        Thread.sleep(300)
        assertFalse("not told in the background", REOPEN in other)
        socket.foreground()
        until { REOPEN in other }
        Thread.sleep(500)
        assertEquals("told once on return", 1, other.count { type -> type == REOPEN })
    }

    @Test
    fun `the process lifecycle closes and reopens the sockets`() {
        val owner = object : LifecycleOwner {
            val registry: LifecycleRegistry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle get() = registry
        }
        owner.registry.currentState = Lifecycle.State.CREATED
        socket.watch(owner.registry)
        socket.subscribe(url(), "fp", "tok") {}
        assertNull("not yet started", server.takeRequest(1500, TimeUnit.MILLISECONDS))
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        assertNotNull("started", server.takeRequest(2, TimeUnit.SECONDS))
        until { opened.get() == 1 && socket.isLive("fp") }
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        until { closed.get() == 1 }
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        assertNotNull("started again", server.takeRequest(2, TimeUnit.SECONDS))
        until { opened.get() == 2 }
    }
}
