// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.ui.list

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pulling the pane beside the drawer down reads the games again. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RefreshTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `pulling the empty pane down refreshes the games`() {
        var refreshes = 0
        rule.setContent {
            GamesPane(
                uiState = GoGameListUiState(),
                onRefresh = { refreshes++ },
                onRetry = {},
                onNewGame = {},
            )
        }
        rule.onRoot().performTouchInput { swipeDown() }
        rule.waitForIdle()
        assertEquals(1, refreshes)
    }
}
