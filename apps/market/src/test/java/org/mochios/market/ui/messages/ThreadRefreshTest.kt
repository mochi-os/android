// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.messages

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.android.websocket.REOPEN
import org.mochios.android.websocket.StreamWsEvent

/**
 * A message thread reads itself again on the server's poke for a new message,
 * and when its stream opens again after being down, having missed any poke
 * sent meanwhile.
 */
class ThreadRefreshTest {

    private fun frame(type: String = "", event: String? = null) = StreamWsEvent(
        type = type,
        created = 0,
        member = null,
        name = null,
        body = null,
        event = event,
        raw = mapOf("type" to type),
    )

    @Test
    fun `a new message's poke reads the thread again`() {
        assertTrue(refreshes(frame(event = "message")))
    }

    @Test
    fun `the stream opening again reads the thread again`() {
        assertTrue(refreshes(frame(type = REOPEN)))
    }

    @Test
    fun `anything else does not`() {
        assertFalse(refreshes(frame(type = "typing")))
    }
}
