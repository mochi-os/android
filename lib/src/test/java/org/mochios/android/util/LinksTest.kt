// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksTest {

    @Test
    fun `web schemes are accepted`() {
        assertTrue(isWebUrl("https://example.org/a?b=c"))
        assertTrue(isWebUrl("http://example.org"))
    }

    @Test
    fun `scheme match is case-insensitive`() {
        assertTrue(isWebUrl("HTTPS://example.org"))
        assertTrue(isWebUrl("Http://example.org"))
    }

    @Test
    fun `non-web schemes are refused`() {
        assertFalse(isWebUrl("tel:+15551234567"))
        assertFalse(isWebUrl("file:///data/data/org.mochios.mochi/x"))
        assertFalse(isWebUrl("javascript:alert(1)"))
        assertFalse(isWebUrl("intent://x#Intent;package=evil;end"))
        assertFalse(isWebUrl("mochi:/feed"))
        assertFalse(isWebUrl("content://provider/x"))
    }

    @Test
    fun `scheme is judged before the first colon, not by substring`() {
        // A web URL later in the string must not rescue a hostile scheme.
        assertFalse(isWebUrl("javascript:void(0)//https://example.org"))
        assertFalse(isWebUrl("intent://https://example.org"))
    }

    @Test
    fun `relative and malformed input is refused`() {
        // No scheme at all — a relative path must not launch.
        assertFalse(isWebUrl("attachments/abc"))
        assertFalse(isWebUrl(""))
    }

    // A peer's id in an asset address on the user's server, which the image
    // request reaches with the session and the token of the app its path names.
    @Test
    fun `a segment escapes whatever would leave it`() {
        assertEquals("p1%3Fx%3D1%26y", segment("p1?x=1&y"))
        assertEquals("a%23b%25c%20d", segment("a#b%c d"))
        assertEquals("%C3%A9t%C3%A9", segment("été"))
    }

    @Test
    fun `a segment keeps an ordinary id as it is`() {
        assertEquals("1ab2c3d4e5f6a7b8c9d0e1f2a3b4c5d6", segment("1ab2c3d4e5f6a7b8c9d0e1f2a3b4c5d6"))
        assertEquals("Az09-._~", segment("Az09-._~"))
    }

    // The server decodes the path before it routes, so an escaped slash would
    // divide it again: ../../people/-/remove reaches people's action.
    @Test
    fun `no segment carries a slash`() {
        assertNull(segment("../../people/-/remove?x=1"))
        assertNull(segment("members/someone"))
        assertNull(segment("/"))
    }

    // Escaping leaves a dot alone and the URL resolves dot segments away, so
    // revision/../asset/avatar would ask for the parent's asset/avatar.
    @Test
    fun `no segment carries a dot segment or nothing`() {
        assertNull(segment(""))
        assertNull(segment("."))
        assertNull(segment(".."))
    }
}
