// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.messages

import androidx.lifecycle.SavedStateHandle
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.i18n.AppContext
import org.mochios.market.FlowServer
import org.mochios.market.settle
import org.mochios.market.until
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * A conversation: a seller opening one names its buyer; one opened by its id
 * alone reads it straight away; the header photo is looked up once; and a
 * message sent while the thread is being read neither vanishes nor appears
 * twice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MessageThreadFlowsTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: FlowServer

    /** The thread's messages as the server holds them. */
    private val messages = Collections.synchronizedList(mutableListOf("""{"id": "m1", "thread": "t1", "body": "Hello"}"""))

    /** How long a read of the thread takes to answer. */
    @Volatile private var readDelay = 0L

    /** How long a send takes to answer. */
    @Volatile private var sendDelay = 0L

    @Volatile private var threadPhoto = "null"

    @Before
    fun begin() {
        AppContext.set(context)
        server = FlowServer()
        server.data("-/threads/create", """{"id": "t1", "listing": "l1", "buyer": "b1", "seller": "s1"}""")
        server.on("-/threads/get") {
            // The answer is what the server holds when the read arrives, delivered late.
            val body = """{"data": {"thread": {"id": "t1", "listing": "l1", "buyer": "b1", "seller": "s1"},
                "messages": [${synchronized(messages) { messages.joinToString(",") }}],
                "listing": {"id": "l1", "title": "Lamp", "photo": $threadPhoto}}}"""
            MockResponse().setBody(body).setBodyDelay(readDelay, TimeUnit.MILLISECONDS)
        }
        server.on("-/messages/send") {
            val saved = """{"id": "m2", "thread": "t1", "body": "Still for sale?"}"""
            messages.add(saved)
            MockResponse().setBody("""{"data": $saved}""").setBodyDelay(sendDelay, TimeUnit.MILLISECONDS)
        }
        server.on("-/messages/read") { MockResponse().setBody("""{"data": null}""") }
        server.data("-/photos/list", """[{"id": "p1", "rank": 0}]""")
    }

    @After
    fun end() = server.stop()

    private fun model(vararg arguments: Pair<String, String>): MessageThreadViewModel =
        MessageThreadViewModel(SavedStateHandle(mapOf(*arguments)), server.repository(context), SessionManager(context))

    private fun loaded(model: MessageThreadViewModel) =
        until("the thread") { model.state.value.thread != null && !model.state.value.isLoading }

    @Test
    fun `a seller opening a conversation from a sale names the buyer`() {
        val model = model("listingId" to "l1", "threadId" to "new", "buyer" to "b1")
        loaded(model)
        val fields = server.of("-/threads/create").single().fields
        assertEquals("l1", fields["listing"])
        assertEquals("b1", fields["buyer"])
    }

    @Test
    fun `a conversation opened by its id alone is read without opening another`() {
        val model = model("threadId" to "t1")
        loaded(model)
        assertEquals(0, server.of("-/threads/create").size)
        assertEquals("l1", model.state.value.listing.id)
    }

    @Test
    fun `the header photo is looked up once across reads`() {
        val model = model("listingId" to "l1", "threadId" to "t1")
        loaded(model)
        model.refresh()
        until("the second read") { server.of("-/threads/get").size == 2 && !model.state.value.isLoading }
        model.refresh()
        until("the third read") { server.of("-/threads/get").size == 3 && !model.state.value.isLoading }
        assertEquals("p1", model.state.value.photo)
        assertEquals(1, server.of("-/photos/list").size)
    }

    @Test
    fun `a photo the thread carries needs no lookup`() {
        threadPhoto = """{"id": "p9", "rank": 0}"""
        val model = model("listingId" to "l1", "threadId" to "t1")
        loaded(model)
        assertEquals("p9", model.state.value.photo)
        assertEquals(0, server.of("-/photos/list").size)
    }

    @Test
    fun `a read that began before a send does not take the sent message away`() {
        val model = model("listingId" to "l1", "threadId" to "t1")
        loaded(model)
        // A poke starts a read that answers late, with the thread as it was.
        readDelay = 800
        model.refresh()
        until("the read to start") { server.of("-/threads/get").size == 2 }
        model.updateDraft("Still for sale?")
        model.sendMessage()
        until("the send") { server.of("-/messages/send").isNotEmpty() && !model.state.value.isSending }
        settle(2_500)
        val ids = model.state.value.messages.map { it.id }
        assertTrue("the sent message stays: $ids", "m2" in ids)
    }

    @Test
    fun `a read that lands during a send does not list the message twice`() {
        val model = model("listingId" to "l1", "threadId" to "t1")
        loaded(model)
        // The send answers late; a poke meanwhile reads a thread already holding it.
        sendDelay = 800
        model.updateDraft("Still for sale?")
        model.sendMessage()
        until("the send to arrive") { server.of("-/messages/send").isNotEmpty() }
        model.refresh()
        settle(2_500)
        val ids = model.state.value.messages.map { it.id }
        assertEquals("each message once: $ids", ids.distinct(), ids)
        assertTrue("the sent message is there: $ids", "m2" in ids)
    }
}
