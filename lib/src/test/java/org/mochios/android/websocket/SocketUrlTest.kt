// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.websocket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The subscription token must never appear in the handshake URL: OkHttp keeps
 * the application interceptors for a WebSocket call, so the logging interceptor
 * writes the URL, credential included, to logcat on release.
 */
class SocketUrlTest {

    @Test
    fun `the handshake url carries only the subscription key`() {
        assertEquals(
            "wss://mochi-os.org/_/websocket?key=abc123def",
            socketUrl("wss://mochi-os.org", "abc123def"),
        )
    }

    @Test
    fun `no token appears in the url for any input`() {
        for (base in listOf("wss://mochi-os.org", "ws://localhost:8081", "wss://self.hosted.example")) {
            val url = socketUrl(base, "abc123def")
            assertFalse("token must never be a query parameter: $url", url.contains("token"))
            assertFalse(url.contains("Bearer"))
        }
    }
}
