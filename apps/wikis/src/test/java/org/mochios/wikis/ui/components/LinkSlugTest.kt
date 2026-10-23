// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** The page a link in a page's body opens, matching the web client. */
class LinkSlugTest {

    @Test
    fun `a bare slug names that page`() {
        assertEquals("changelog", linkSlug("changelog"))
    }

    @Test
    fun `a page link written relative to the page names the page beside it`() {
        assertEquals("changelog", linkSlug("../changelog"))
        assertEquals("changelog", linkSlug("./changelog"))
    }

    @Test
    fun `a fragment or query is dropped`() {
        assertEquals("changelog", linkSlug("changelog#install"))
        assertEquals("changelog", linkSlug("../changelog?x=1"))
    }
}
