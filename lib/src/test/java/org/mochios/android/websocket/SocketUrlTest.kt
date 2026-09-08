// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.websocket

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The server authorises the upgrade from the token in the query string. A
 * handshake that omits it connects and then delivers nothing, which is silent:
 * the socket looks healthy and no event ever arrives.
 */
class SocketUrlTest {

    @Test
    fun `the handshake url carries the key and the token`() {
        assertEquals(
            "wss://mochi-os.org/_/websocket?key=abc123def&token=jwt.body.sig",
            socketUrl("wss://mochi-os.org", "abc123def", "jwt.body.sig"),
        )
    }

    @Test
    fun `a tokenless handshake carries only the key`() {
        for (base in listOf("wss://mochi-os.org", "ws://localhost:8081")) {
            assertEquals("$base/_/websocket?key=abc123def", socketUrl(base, "abc123def"))
            assertEquals("$base/_/websocket?key=abc123def", socketUrl(base, "abc123def", ""))
        }
    }
}
