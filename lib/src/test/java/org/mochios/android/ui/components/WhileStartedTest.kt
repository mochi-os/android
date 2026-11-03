// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A screen's poll runs while the screen is started, and not behind it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WhileStartedTest {
    @get:Rule
    val compose = createComposeRule()

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun `runs while started, stops in the background and starts again on return`() {
        val owner = Owner().apply { registry.currentState = Lifecycle.State.RESUMED }
        var runs = 0
        var running = 0
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                WhileStarted {
                    runs++
                    running++
                    try {
                        awaitCancellation()
                    } finally {
                        running--
                    }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(1, runs)
        assertEquals(1, running)

        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.waitForIdle()
        assertEquals("stopped behind the home screen", 0, running)

        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitForIdle()
        assertEquals("run again from the start on return", 2, runs)
        assertEquals(1, running)
    }
}
