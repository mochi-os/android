// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import org.junit.Assert.assertFalse
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
}
