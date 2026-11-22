// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.`object`

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.android.ui.components.SaveStatus
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.crm.CrmServer
import org.mochios.crm.model.CrmObject
import org.mochios.crm.settle
import org.mochios.crm.until
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * An open object read again after a frame keeps what is being typed and a
 * parent being saved, never shows another object's answer, sends "no
 * parent" so the server reads it, and says when a link change fails.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ObjectEditsTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: CrmServer
    private lateinit var socket: MochiWebSocket
    private lateinit var model: ObjectDetailViewModel
    private val failures = mutableListOf<MochiError>()
    private val jobs = mutableListOf<Job>()

    /** What the server holds for o1. */
    @Volatile private var title = "Old"
    @Volatile private var status = "open"
    @Volatile private var parent = "x1"

    /** Holds the writes until released. */
    private val writes = CountDownLatch(1)

    /** When set, holds reads of o1 until released. */
    @Volatile private var reads: CountDownLatch? = null

    /** Counted down as each held read of o1 arrives. */
    @Volatile private var arrived = CountDownLatch(1)

    @Before
    fun begin() {
        server = CrmServer(context)
        server.on("-/users/search") { MockResponse().setResponseCode(403).setBody("""{"error": "Not allowed"}""") }
        server.on("p1/-/objects/o1") {
            reads?.let { held ->
                arrived.countDown()
                held.await(5, TimeUnit.SECONDS)
            }
            CrmServer.ok("""{"object": {"id": "o1", "crm": "p1", "parent": "$parent"}, "values": {"title": "$title", "status": "$status"}}""")
        }
        server.data("p1/-/objects/o2", """{"object": {"id": "o2", "crm": "p1", "parent": ""}, "values": {"title": "Second"}}""")
        val held: (okhttp3.mockwebserver.RecordedRequest) -> MockResponse = {
            writes.await(5, TimeUnit.SECONDS)
            CrmServer.ok("{}")
        }
        server.on("p1/-/objects/o1/values/title", held)
        server.on("p1/-/objects/o1/update", held)
        socket = server.socket()
        model = ObjectDetailViewModel(server.repository(), server.session, socket)
        jobs += CoroutineScope(Dispatchers.Main).launch { model.actionFailed.collect { failures += it } }
        jobs += CoroutineScope(Dispatchers.Main).launch { model.saveFailed.collect { failures += it } }
        model.loadWithInitialObject("p1", "o1", CrmObject(id = "o1", crm = "p1", parent = "x1", values = mapOf("title" to "Old", "status" to "open")))
        until("the socket") { server.live != null && socket.isLive("p1") }
        settle(300)
    }

    @After
    fun end() {
        writes.countDown()
        reads?.countDown()
        jobs.forEach { job -> job.cancel() }
        socket.disconnectAll()
        server.stop()
    }

    private fun value(field: String) = model.uiState.value.obj?.values?.get(field)

    private fun frame(type: String) = server.live!!.send("""{"type": "$type", "id": "o1"}""")

    @Test
    fun `a frame shows another person's change`() {
        title = "Theirs"
        status = "done"
        frame("values/update")
        until("the read") { value("status") == "done" }
        assertEquals("Theirs", value("title"))
    }

    @Test
    fun `a frame keeps the text being typed while its save waits, and takes the other fields`() {
        model.setValue("title", "New")
        status = "done"
        frame("values/update")
        until("the read") { value("status") == "done" }
        assertEquals("New", value("title"))
        // Now its save is in flight, still unanswered.
        until("the save") { server.of("p1/-/objects/o1/values/title").isNotEmpty() }
        status = "won"
        frame("values/update")
        until("the second read") { value("status") == "won" }
        assertEquals("New", value("title"))
    }

    @Test
    fun `a read served before a save answered does not undo it`() {
        model.setValue("title", "New")
        until("the save") { server.of("p1/-/objects/o1/values/title").isNotEmpty() }
        val held = CountDownLatch(1)
        reads = held
        frame("values/update")
        until("the read") { arrived.count == 0L }
        // The save answers while the read, served before it, is out.
        writes.countDown()
        until("the save's answer") { model.uiState.value.saveStatus == SaveStatus.Saved }
        held.countDown()
        settle()
        assertEquals("New", value("title"))
    }

    @Test
    fun `an answer for the object the sheet has left is dropped`() {
        val held = CountDownLatch(1)
        reads = held
        frame("object/update")
        until("the read") { arrived.count == 0L }
        model.loadWithInitialObject("p1", "o2", CrmObject(id = "o2", crm = "p1", values = mapOf("title" to "Second")))
        // The second object's own read has answered; the first's comes last.
        until("the second object's read") { server.of("p1/-/objects/o2").isNotEmpty() }
        settle()
        assertEquals("o2", model.uiState.value.obj?.id)
        held.countDown()
        settle()
        assertEquals("o2", model.uiState.value.obj?.id)
        assertEquals("Second", value("title"))
    }

    @Test
    fun `no parent goes as an empty parent the server reads, and a frame keeps it while it saves`() {
        model.updateParent("")
        until("the update") { server.of("p1/-/objects/o1/update").isNotEmpty() }
        val update = server.of("p1/-/objects/o1/update").single()
        assertEquals("", update.json().get("parent").asString)
        status = "done"
        frame("object/update")
        until("the read") { value("status") == "done" }
        assertEquals("", model.uiState.value.obj?.parent)
    }

    @Test
    fun `a link the server refuses to add or remove is said`() {
        server.refuse("p1/-/objects/o1/links/create", "No link")
        server.refuse("p1/-/objects/o2/links/create", "No reverse link")
        server.refuse("p1/-/objects/o1/links/delete", "No unlink")
        server.refuse("p1/-/objects/o2/links/delete", "No reverse unlink")
        model.createLink("o2", "relates")
        model.createReverseLink("o2", "blocks")
        model.deleteOutgoingLink("o2", "relates")
        model.deleteIncomingLink("o2", "blocks")
        until("four failures") { failures.size == 4 }
        assertEquals(
            setOf("No link", "No reverse link", "No unlink", "No reverse unlink"),
            failures.map { failure -> failure.message }.toSet(),
        )
        assertNull(model.uiState.value.error)
    }

    @Test
    fun `a refused mention search reaches the field instead of reading as no one found`() {
        val failure = runCatching { kotlinx.coroutines.runBlocking { model.searchUsers("al") } }.exceptionOrNull()
        org.junit.Assert.assertTrue("THE POINT: the search's refusal is thrown, got $failure", failure != null)
    }
}
