// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm

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
import org.mochios.crm.ui.crm.rememberReturnRefresh
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A refresh owed for something made on another screen - a created object or
 * an added column - is paid once when the CRM resumes, and only when owed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReturnRefreshTest {

    @get:Rule
    val rule = createComposeRule()

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun `a refresh owed before leaving is paid once on the way back, and none when nothing is owed`() {
        val owner = Owner()
        owner.registry.currentState = Lifecycle.State.RESUMED
        var refreshed = 0
        var owe: () -> Unit = {}
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                owe = rememberReturnRefresh { refreshed++ }
            }
        }
        rule.waitForIdle()
        // Resuming with nothing owed reads nothing again.
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        rule.waitForIdle()
        assertEquals(0, refreshed)
        // Leaving for the create screen owes a refresh, paid on the way back.
        rule.runOnIdle {
            owe()
            owner.registry.currentState = Lifecycle.State.STARTED
        }
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        rule.waitForIdle()
        assertEquals(1, refreshed)
        // Paid once: the next return reads nothing again.
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        rule.waitForIdle()
        assertEquals(1, refreshed)
    }
}
