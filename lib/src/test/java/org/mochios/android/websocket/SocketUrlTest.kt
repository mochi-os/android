// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.websocket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The subscription token used to ride in the handshake URL. That leaks: a URL
 * is written to every access log between the phone and core, and the token is
 * a year-long credential.
 *
 * It travels in an Authorization header now, which core has read first since
 * October 2025. The query form existed for browsers, which cannot set a header
 * on a WebSocket handshake; OkHttp can, so the client has no use for it. These
 * assert the URL carries no credential, so a regression that puts it back fails
 * here — as one did in 8803131eb95, which also inverted these two tests.
 */
class SocketUrlTest {

    @Test
    fun `the handshake url carries only the subscription key`() {
        assertEquals(
            "https://mochi-os.org/_/websocket?key=abc123def",
            socketUrl("https://mochi-os.org", "abc123def").toString(),
        )
    }

    @Test
    fun `no token appears in the url for any input`() {
        for (base in listOf("https://mochi-os.org", "http://localhost:8081", "https://self.hosted.example")) {
            val url = socketUrl(base, "abc123def").toString()
            assertFalse("token must never be a query parameter: $url", url.contains("token"))
            assertFalse(url.contains("Bearer"))
        }
    }

    // A key can come from another server: one holding & or # must stay one
    // value of the one parameter.
    @Test
    fun `a key is escaped into one query value`() {
        val url = socketUrl("https://mochi-os.org", "a&token=x#b%c")!!
        assertEquals(listOf("key"), url.queryParameterNames.toList())
        assertEquals("a&token=x#b%c", url.queryParameter("key"))
        assertEquals(null, url.fragment)
    }

    @Test
    fun `the url is built on the server's origin alone`() {
        assertEquals(
            "http://localhost:8081/_/websocket?key=k",
            socketUrl("http://localhost:8081/login/", "k").toString(),
        )
    }

    @Test
    fun `a blank key or an unreadable server opens nothing`() {
        assertNull(socketUrl("https://mochi-os.org", " "))
        assertNull(socketUrl("not a server", "k"))
    }
}
