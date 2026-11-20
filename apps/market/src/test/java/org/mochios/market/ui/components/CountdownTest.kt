// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An auction's time left ticks, by the server's clock rather than the
 * phone's, and the page is read again the moment it runs out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CountdownTest {

    @get:Rule
    val rule = createComposeRule()

    private val start = 1_000_000L

    /** The phone's clock, moving with the test's. */
    private fun clock() = start + rule.mainClock.currentTime / 1000L

    private var left = -1L
    private var ended = 0

    private fun show(ends: Long, offset: Long = 0L) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            left = rememberRemaining(ends, offset, onEnded = { ended += 1 }, clock = ::clock)
            Text("$left")
        }
        rule.mainClock.advanceTimeByFrame()
    }

    private fun pass(milliseconds: Long) {
        rule.mainClock.advanceTimeBy(milliseconds)
    }

    @Test
    fun `the time left ticks and its end is reported once`() {
        show(ends = clock() + 3)
        assertEquals(3, left)
        pass(1_000)
        assertEquals(2, left)
        pass(2_000)
        assertEquals(0, left)
        assertEquals(1, ended)
        pass(5_000)
        assertEquals(1, ended)
    }

    @Test
    fun `the time left is the server's`() {
        // The phone's clock is an hour behind the server's.
        show(ends = clock() + 3_600 + 60, offset = 3_600)
        assertEquals(60, left)
    }

    @Test
    fun `an auction already over when shown does not read the page again`() {
        show(ends = clock() - 10)
        pass(3_000)
        assertEquals(0, left)
        assertEquals(0, ended)
    }
}
