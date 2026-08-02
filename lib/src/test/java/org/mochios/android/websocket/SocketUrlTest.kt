// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.websocket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The subscription token used to ride in the handshake URL. That leaks: OkHttp's
 * RealWebSocket.connect() preserves the client's application interceptors, so
 * the logging interceptor — which is not gated on a debug build — writes the
 * full URL, and with it a year-long credential, into logcat on release.
 *
 * It travels in an Authorization header now. These assert the URL carries no
 * credential, so a regression that puts it back fails here.
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
