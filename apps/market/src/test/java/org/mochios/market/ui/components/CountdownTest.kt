// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An auction's time left moves when its label would change, by the server's
 * clock rather than the phone's, and the page is read again the moment it runs
 * out, or when it is shown again after running out in the background.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CountdownTest {

    @get:Rule
    val rule = createComposeRule()

    private val start = 1_000_000L

    /** Seconds the phone slept, which pass on its clock but not for delay(). */
    private var slept = 0L

    /** The phone's clock, moving with the test's. */
    private fun clock() = start + rule.mainClock.currentTime / 1000L + slept

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private val owner = Owner().apply { registry.currentState = Lifecycle.State.RESUMED }

    private var left = -1L
    private var ended = 0

    private fun show(ends: Long, offset: Long = 0L) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                left = rememberRemaining(ends, offset, onEnded = { ended += 1 }, clock = ::clock)
                Text("$left")
            }
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

    @Test
    fun `while it shows days the time left moves on the hour`() {
        show(ends = clock() + 2 * 86_400 + 100)
        assertEquals(172_900, left)
        pass(1_000)
        assertEquals(172_900, left)
        // 2d 0h 1m 40s reads "2d 0h" until 101 seconds on, when it reads "1d 23h".
        pass(100_000)
        assertEquals(172_799, left)
    }

    @Test
    fun `while it shows hours the time left moves on the minute`() {
        show(ends = clock() + 3_600 + 30)
        assertEquals(3_630, left)
        pass(1_000)
        assertEquals(3_630, left)
        pass(30_000)
        assertEquals(3_599, left)
    }

    @Test
    fun `the clock is read again when the screen is shown after the phone slept`() {
        show(ends = clock() + 86_400 + 3_600 + 100)
        assertEquals(90_100, left)
        owner.registry.currentState = Lifecycle.State.CREATED
        slept = 7_200
        owner.registry.currentState = Lifecycle.State.RESUMED
        pass(50)
        assertEquals(82_900, left)
    }

    @Test
    fun `an end passed in the background is reported once when shown again`() {
        show(ends = clock() + 86_400 + 200)
        owner.registry.currentState = Lifecycle.State.CREATED
        slept = 90_000
        pass(1_000)
        assertEquals(0, ended)
        owner.registry.currentState = Lifecycle.State.RESUMED
        pass(50)
        assertEquals(0, left)
        assertEquals(1, ended)
        owner.registry.currentState = Lifecycle.State.CREATED
        owner.registry.currentState = Lifecycle.State.RESUMED
        pass(50)
        assertEquals(1, ended)
    }
}
