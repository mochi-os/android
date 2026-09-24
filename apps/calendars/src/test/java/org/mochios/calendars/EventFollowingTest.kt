// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.calendars.ui.editor.following

/**
 * An end whose zone moved east can fall before the start by the instant;
 * it moves on by whole days until it follows, as an eastbound arrival does.
 */
class EventFollowingTest {

    private val begins = 1_790_000_000L

    @Test
    fun `an end that already follows is left as it is`() {
        assertEquals(begins + 3_600, following(begins, begins + 3_600))
        assertEquals(begins, following(begins, begins))
    }

    @Test
    fun `an end nine hours before the start moves on a day`() {
        assertEquals(begins - 9 * 3_600 + 86_400, following(begins, begins - 9 * 3_600))
    }

    @Test
    fun `an end two days and an hour before moves on three days`() {
        val ends = begins - 2 * 86_400 - 3_600
        assertEquals(ends + 3 * 86_400, following(begins, ends))
    }
}
