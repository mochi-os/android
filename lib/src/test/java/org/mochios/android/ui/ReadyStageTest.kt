// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Below Android 13 the app rebuilds its Activity itself after a fresh sign-in
 * whose language differs from the phone's. The view model, and the Ready that
 * asked for the rebuild, outlive the Activity: left asking, every rebuilt
 * Activity asked again, without end.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ReadyStageTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the rebuild is asked for once, and the rebuilt activity shows the app`() {
        // What the view model holds, kept across the rebuild as it is.
        val held = MutableStateFlow<AuthStage>(AuthStage.Ready(epoch = 7, recreateForLocale = true))
        var requests = 0
        var activity by mutableIntStateOf(0)
        rule.setContent {
            // A rebuild composes the host afresh in a new Activity.
            key(activity) {
                val stage by held.collectAsState()
                ReadyStage(
                    stage = stage as AuthStage.Ready,
                    onLocaleApplied = { held.update { it.localeApplied() } },
                    onLocaleChangeRequested = {
                        requests++
                        // Rebuilds a few times at most, so a loop fails rather than hangs.
                        if (requests < 4) activity++
                    },
                ) {
                    Text("App")
                }
            }
        }
        rule.waitForIdle()
        assertEquals(1, requests)
        rule.onNodeWithText("App").assertIsDisplayed()
        // The same Ready, so the app's saved state is not thrown away.
        assertEquals(AuthStage.Ready(epoch = 7, recreateForLocale = false), held.value)
    }

    @Test
    fun `a ready that asks for no rebuild shows the app at once`() {
        var requests = 0
        rule.setContent {
            ReadyStage(AuthStage.Ready(epoch = 1), onLocaleApplied = {}, onLocaleChangeRequested = { requests++ }) {
                Text("App")
            }
        }
        rule.onNodeWithText("App").assertIsDisplayed()
        assertEquals(0, requests)
    }
}
