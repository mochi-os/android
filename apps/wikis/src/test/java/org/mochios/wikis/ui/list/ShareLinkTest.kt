// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.list

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which search-box input probes a remote peer rather than the local directory.
 * A share link names a wiki this instance may never have heard of, so a
 * directory search for it returns nothing.
 */
class ShareLinkTest {

    @Test
    fun `a pasted share link probes`() {
        assertTrue(isShareLink("mochi://12D3KooWPd68TanRD1mgWmPZJ3iRantH8z3nFBFpsFSTodsxyMu7/eAUKHHFaQ"))
    }

    @Test
    fun `pasted whitespace does not defeat the branch`() {
        assertTrue(isShareLink("  mochi://peer/wiki\n"))
    }

    @Test
    fun `an ordinary search term goes to the directory`() {
        assertFalse(isShareLink("recipes"))
        assertFalse(isShareLink("mochi"))
    }

    @Test
    fun `a web URL is not a share link`() {
        // https:// links are not what -/probe accepts; it 400s on them.
        assertFalse(isShareLink("https://mochi-os.org/wikis/eAUKHHFaQ"))
    }
}
