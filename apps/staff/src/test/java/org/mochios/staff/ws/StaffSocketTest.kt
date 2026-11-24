// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ws

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.staff.StaffServer
import org.mochios.staff.StaffServer.Companion.ok
import org.mochios.staff.ui.appeals.AppealsViewModel
import org.mochios.staff.ui.disputes.DisputesViewModel
import org.mochios.staff.ui.listings.ListingsViewModel
import org.mochios.staff.ui.moderation.ModerationLogViewModel
import org.mochios.staff.ui.reports.ReportsViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Each staff list holds the staff-events socket for as long as its view model
 * lives, so the one socket stays open under a pushed screen: a frame for the
 * list reads it again, the socket opening after a gap does too, and the socket
 * closes only when the last list is gone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StaffSocketTest {

    private val server = StaffServer { request ->
        val path = request.requestUrl!!.encodedPath
        when {
            path.endsWith("-/reports/list") -> ok("""{"reports": [], "total": 0, "next": ""}""")
            path.endsWith("-/disputes/list") -> ok("""{"disputes": [], "total": 0, "next": ""}""")
            path.endsWith("-/appeals/list") -> ok("""{"appeals": [], "total": 0, "next": ""}""")
            path.endsWith("-/listings/pending") -> ok("""{"listings": [], "total": 0, "next": ""}""")
            path.endsWith("-/moderation/log") -> ok("""{"log": [], "total": 0, "next": ""}""")
            else -> null
        }
    }

    private val stores = mutableListOf<ViewModelStore>()

    @Before
    fun begin() {
        server.start()
    }

    @After
    fun end() {
        stores.forEach { store -> store.clear() }
        server.stop()
    }

    /** [make]'s view model in a store of its own, as a back stack entry holds one; [ViewModelStore.clear] is its leaving. */
    private inline fun <reified T : ViewModel> held(crossinline make: () -> T): Pair<T, ViewModelStore> {
        val store = ViewModelStore()
        stores += store
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <V : ViewModel> create(modelClass: Class<V>): V = make() as V
        }
        return ViewModelProvider(store, factory)[T::class.java] to store
    }

    private fun reads(path: String) = server.made(path).size

    private fun open() = server.until { server.peer != null && server.socket.isLive(STAFF_EVENTS) }

    @Test
    fun `every list that follows staff events reads again on its topic`() {
        held { ReportsViewModel(server.repository, server.socket, server.session, SavedStateHandle()) }
        held { DisputesViewModel(server.repository, server.socket, server.session, SavedStateHandle()) }
        held { AppealsViewModel(server.repository, server.socket, server.session) }
        held { ListingsViewModel(server.repository, server.socket, server.session, SavedStateHandle()) }
        held { ModerationLogViewModel(server.repository, server.socket, server.session, SavedStateHandle()) }
        open()
        server.until { reads("-/moderation/log") == 1 && reads("-/listings/pending") == 1 }
        assertEquals(1, server.handshakes.get())

        server.peer!!.send("""{"topic": "staff/report", "object": "r1"}""")
        server.until { reads("-/reports/list") == 2 }
        server.peer!!.send("""{"topic": "staff/dispute", "object": "d1"}""")
        server.until { reads("-/disputes/list") == 2 }
        server.peer!!.send("""{"topic": "staff/moderation", "object": "l1"}""")
        server.until { reads("-/appeals/list") == 2 && reads("-/listings/pending") == 2 && reads("-/moderation/log") == 2 }
        server.idle()
        assertEquals(2, reads("-/reports/list"))
        assertEquals(2, reads("-/disputes/list"))
    }

    @Test
    fun `the socket opening again after a gap reads every list again`() {
        held { ReportsViewModel(server.repository, server.socket, server.session, SavedStateHandle()) }
        open()
        server.until { reads("-/reports/list") == 1 }
        server.socket.background()
        server.until { !server.socket.isLive(STAFF_EVENTS) }
        server.socket.foreground()
        server.until { reads("-/reports/list") == 2 }
    }

    @Test
    fun `the socket stays while any list holds it and closes with the last`() {
        val (_, reports) = held { ReportsViewModel(server.repository, server.socket, server.session, SavedStateHandle()) }
        val (_, disputes) = held { DisputesViewModel(server.repository, server.socket, server.session, SavedStateHandle()) }
        open()
        reports.clear()
        server.idle()
        assertTrue(server.socket.isLive(STAFF_EVENTS))
        server.peer!!.send("""{"topic": "staff/dispute", "object": "d1"}""")
        server.until { reads("-/disputes/list") == 2 }
        disputes.clear()
        server.until { !server.socket.isLive(STAFF_EVENTS) }
        assertFalse(server.socket.isLive(STAFF_EVENTS))
        assertEquals(1, server.handshakes.get())
    }
}
