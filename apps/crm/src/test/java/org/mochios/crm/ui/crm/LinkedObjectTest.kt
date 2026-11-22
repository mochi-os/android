// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.crm

import androidx.lifecycle.SavedStateHandle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.crm.CrmServer
import org.mochios.crm.lib.ActiveViewStore
import org.mochios.crm.lib.CollapsedColumnsStore
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The item a link or a notification named opens once: coming back to the
 * board from Copy, Settings or Design, or the activity being made again,
 * leaves the sheet as the user left it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LinkedObjectTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: CrmServer
    private lateinit var socket: MochiWebSocket

    @Before
    fun begin() {
        server = CrmServer(context)
        socket = server.socket()
    }

    @After
    fun end() {
        socket.disconnectAll()
        server.stop()
    }

    private fun model(handle: SavedStateHandle) = CrmViewModel(
        handle,
        server.repository(),
        socket,
        server.session,
        ActiveViewStore(context),
        CollapsedColumnsStore(context),
    )

    @Test
    fun `the linked item opens on arrival and not again on a return`() {
        val handle = SavedStateHandle(mapOf("crmId" to "c1", "objectId" to "o1"))
        val model = model(handle)
        model.openLinked("o1")
        assertEquals("o1", model.uiState.value.selectedObjectId)
        model.selectObject(null, refresh = false)
        // The screen entering composition again on the way back.
        model.openLinked("o1")
        assertNull(model.uiState.value.selectedObjectId)
        // The activity made again: a new model over the saved state.
        val again = model(handle)
        again.openLinked("o1")
        assertNull(again.uiState.value.selectedObjectId)
    }

    @Test
    fun `no linked item opens nothing`() {
        val model = model(SavedStateHandle(mapOf("crmId" to "c1")))
        model.openLinked(null)
        assertNull(model.uiState.value.selectedObjectId)
    }
}
