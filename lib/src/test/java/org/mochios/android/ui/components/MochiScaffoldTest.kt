// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A screen's content keeps clear of the keyboard: the room it takes is
 * padding the content gives up, so a field typed in stays above it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MochiScaffoldTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the content gives up the keyboard's height at its foot`() {
        rule.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(rule.activity.window, false) }
        var bottom = 0f
        rule.setContent {
            val density = LocalDensity.current
            MochiScaffold(title = "Contact") { padding ->
                bottom = with(density) { padding.calculateBottomPadding().toPx() }
            }
        }
        rule.runOnUiThread {
            val keyboard = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 600))
                .setVisible(WindowInsetsCompat.Type.ime(), true)
                .build()
            ViewCompat.dispatchApplyWindowInsets(rule.activity.window.decorView, keyboard)
        }
        rule.waitForIdle()
        assertTrue("the content's foot is padded $bottom px for a 600 px keyboard", bottom >= 600f)
    }
}
