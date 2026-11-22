// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.`object`

import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.ui.components.LoadState
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.crm.CrmServer
import org.mochios.crm.model.CrmObject
import org.mochios.crm.settle
import org.mochios.crm.until
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * An object's activity is read a page at a time, as the web reads it, rather
 * than its newest hundred entries alone; a change read again keeps the older
 * pages already read, and a failed first read is said rather than shown as
 * no activity.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActivityPagesTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: CrmServer
    private lateinit var socket: MochiWebSocket
    private lateinit var model: ObjectDetailViewModel

    /** The log, newest first: e149 is the newest of 150 entries. */
    @Volatile private var log = (149 downTo 0).map { index -> "e$index" }

    /** The offset of each activity read, in order. */
    private val offsets = ConcurrentLinkedQueue<String>()

    /** The limit of each activity read, in order. */
    private val limits = ConcurrentLinkedQueue<String>()

    @Volatile private var refused = false

    @Before
    fun begin() {
        server = CrmServer(context)
        server.data("c1/-/objects/o1", """{"object": {"id": "o1", "crm": "c1"}}""")
        server.on("c1/-/objects/o1/activity") { request ->
            val url = request.requestUrl!!
            val offset = url.queryParameter("offset").orEmpty()
            val limit = url.queryParameter("limit").orEmpty()
            offsets += offset
            limits += limit
            if (refused) {
                MockResponse().setResponseCode(500).setBody("""{"error": "Activity unavailable"}""")
            } else {
                val page = log.drop(offset.toInt()).take(limit.toInt())
                CrmServer.ok(page.joinToString(",", "{\"activities\": [", "]}") { id ->
                    """{"id": "$id", "user": "u1", "name": "Ada", "action": "commented", "display": {}}"""
                })
            }
        }
        socket = server.socket()
        model = ObjectDetailViewModel(server.repository(), server.session, socket)
    }

    @After
    fun end() {
        socket.disconnectAll()
        server.stop()
    }

    private val activity get() = model.uiState.value.activity

    private fun open() {
        model.loadWithInitialObject("c1", "o1", CrmObject(id = "o1", crm = "c1"), "owner")
        until("the first page") { activity.pages == 1 }
    }

    @Test
    fun `the activity is read a page at a time, from where the last page ended`() {
        open()
        assertEquals((149 downTo 50).map { index -> "e$index" }, activity.entries.map { entry -> entry.id })
        assertTrue(activity.more)
        model.loadOlderActivity()
        until("the second page") { activity.pages == 2 }
        assertEquals(listOf("0", "100"), offsets.toList())
        assertEquals(listOf("100", "100"), limits.toList())
        assertEquals((149 downTo 0).map { index -> "e$index" }, activity.entries.map { entry -> entry.id })
        assertFalse("a short page is the last", activity.more)
        model.loadOlderActivity()
        settle(300)
        assertEquals(2, offsets.size)
    }

    @Test
    fun `a change read again keeps the older pages read, each entry once`() {
        open()
        model.loadOlderActivity()
        until("the second page") { activity.pages == 2 }
        until("the socket") { server.live != null && socket.isLive("c1") }
        log = listOf("e150") + log
        server.live!!.send("""{"type": "values/update", "id": "o1"}""")
        until("the read again") { activity.entries.firstOrNull()?.id == "e150" }
        val ids = activity.entries.map { entry -> entry.id }
        assertEquals((150 downTo 0).map { index -> "e$index" }, ids)
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(2, activity.pages)
    }

    @Test
    fun `a failed first read is said, and retried from the list`() {
        refused = true
        model.loadWithInitialObject("c1", "o1", CrmObject(id = "o1", crm = "c1"), "owner")
        until("the failure") { activity.load is LoadState.Failed }
        assertEquals("Activity unavailable", (activity.load as LoadState.Failed).error.message)
        refused = false
        model.loadOlderActivity()
        until("the retry") { activity.pages == 1 }
        assertEquals(LoadState.Idle, activity.load)
        assertEquals(100, activity.entries.size)
    }
}
