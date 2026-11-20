// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.feed

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The feed's menu offers the AI sort only where the server has an AI account
 * to run it, as the web does; without one, a feed saved in it lists in
 * interest order on the server and shows that order ticked.
 */
class SortOptionsTest {

    @Test
    fun `with an AI account every sort is offered`() {
        assertEquals(listOf("ai", "interests", "new", "hot", "top"), sortOptions(true))
    }

    @Test
    fun `without one the AI sort is left out`() {
        assertEquals(listOf("interests", "new", "hot", "top"), sortOptions(false))
    }

    @Test
    fun `a saved AI sort with no account to run it shows as interests`() {
        assertEquals("interests", shownSort("ai", false))
        assertEquals("ai", shownSort("ai", true))
        assertEquals("new", shownSort("new", false))
    }
}
