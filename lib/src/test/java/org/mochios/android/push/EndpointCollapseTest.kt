// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A push endpoint judged local is delivered to the server itself; judged
 * foreign, the server POSTs RFC 8030 to its own stubbed inbound handler. Either
 * error loses the push.
 */
class EndpointCollapseTest {

    private val server = "https://mochi-os.org"

    @Test
    fun `our own endpoint collapses to its path`() {
        assertEquals(
            "/notifications/-/push/inbound/abc123",
            collapseLocalEndpoint(
                "https://mochi-os.org/notifications/-/push/inbound/abc123",
                server,
            ),
        )
    }

    @Test
    fun `an explicit default port is still ours`() {
        assertEquals(
            "/notifications/-/push/inbound/abc123",
            collapseLocalEndpoint(
                "https://mochi-os.org:443/notifications/-/push/inbound/abc123",
                server,
            ),
        )
    }

    /** A third-party distributor must keep its absolute URL. */
    @Test
    fun `a foreign host is left absolute`() {
        val foreign = "https://ntfy.sh/mochi-abc123"
        assertEquals(foreign, collapseLocalEndpoint(foreign, server))
    }

    @Test
    fun `same host on a different port or scheme is not ours`() {
        val otherPort = "https://mochi-os.org:8443/notifications/-/push/inbound/abc123"
        assertEquals(otherPort, collapseLocalEndpoint(otherPort, server))
        val cleartext = "http://mochi-os.org/notifications/-/push/inbound/abc123"
        assertEquals(cleartext, collapseLocalEndpoint(cleartext, server))
    }

    @Test
    fun `a lookalike host is not ours`() {
        val lookalike = "https://mochi-os.org.attacker.example/notifications/-/push/inbound/x"
        assertEquals(lookalike, collapseLocalEndpoint(lookalike, server))
    }

    /** Credentials or a fragment are never something our distributor issues. */
    @Test
    fun `credentials or a fragment are never treated as ours`() {
        val credentials = "https://user:secret@mochi-os.org/notifications/-/push/inbound/abc"
        assertEquals(credentials, collapseLocalEndpoint(credentials, server))
        val fragment = "https://mochi-os.org/notifications/-/push/inbound/abc#x"
        assertEquals(fragment, collapseLocalEndpoint(fragment, server))
    }

    @Test
    fun `unusable input is left alone`() {
        assertEquals("not a url", collapseLocalEndpoint("not a url", server))
        val ours = "https://mochi-os.org/notifications/-/push/inbound/abc"
        assertEquals(ours, collapseLocalEndpoint(ours, ""))
    }
}
