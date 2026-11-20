// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.R
import org.mochios.android.api.MochiError
import org.mochios.android.i18n.AppContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A list asks for its next page as its end comes into view, but not again
 * after that page failed: the failure is shown as the list's last row, and
 * the next page is asked for when the user retries.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InfiniteListTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    /** Three rows, so the list's end is in view from the start. */
    private val rows = listOf("Teapot", "Kettle", "Cup")

    private var asked = 0

    private fun show(load: LoadState) {
        AppContext.set(context)
        rule.setContent {
            InfiniteList(
                items = rows,
                load = load,
                hasMore = true,
                onLoadMore = { asked += 1 },
            ) { row -> Text(row) }
        }
        rule.waitForIdle()
    }

    @Test
    fun `a list whose end is in view asks for the next page`() {
        show(LoadState.Idle)
        assertEquals(1, asked)
    }

    @Test
    fun `a page that failed is not asked for again until the user retries`() {
        show(LoadState.Failed(MochiError.Unknown("Comptroller is not available")))
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        assertEquals(0, asked)
        rule.onNodeWithText("Comptroller is not available").assertExists()
        rule.onNodeWithText(context.getString(R.string.common_retry)).performClick()
        rule.waitForIdle()
        assertEquals(1, asked)
    }

    @Test
    fun `a failure after a page was asked for stops the asking`() {
        // The caller's state as the page it asked for fails: loading, then failed.
        var load by mutableStateOf<LoadState>(LoadState.Idle)
        AppContext.set(context)
        rule.setContent {
            InfiniteList(
                items = rows,
                load = load,
                hasMore = true,
                onLoadMore = {
                    asked += 1
                    load = LoadState.Loading
                },
            ) { row -> Text(row) }
        }
        rule.waitForIdle()
        assertEquals(1, asked)
        load = LoadState.Failed(MochiError.NetworkError())
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        assertEquals(1, asked)
    }
}
