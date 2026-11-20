// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.feed

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.android.i18n.AppContext
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The feed asks for its next page as the reader nears the end. A page that
 * fails changes nothing the reader did, so it is not asked for again on its
 * own: the last post shows the failure with a retry, and moving to another
 * post asks again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class LoadMoreEffectTest {

    @get:Rule
    val rule = createComposeRule()

    private var loadingMore by mutableStateOf(false)
    private var asked = 0
    private lateinit var pager: PagerState

    /** Five posts, the reader on the last; asking marks a page on its way. */
    private fun show() {
        rule.setContent {
            pager = rememberPagerState(initialPage = 4, pageCount = { 5 })
            LoadMoreEffect(pager, 5, hasMore = true, loading = false, loadingMore = loadingMore) {
                asked++
                loadingMore = true
            }
            VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { index -> Text("post $index") }
        }
        rule.waitForIdle()
    }

    @Test
    fun `a failed page is not asked for again until the reader moves`() {
        show()
        assertEquals(1, asked)
        // The page fails: it is no longer on its way, and nothing else changed.
        loadingMore = false
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        assertEquals("THE POINT: the failed page is not asked for again on its own", 1, asked)

        rule.runOnIdle { pager.requestScrollToPage(3) }
        rule.waitForIdle()
        assertEquals("moving to another post asks again", 2, asked)
    }

    @Test
    fun `the last post shows why the next page failed, and retries`() {
        AppContext.set(RuntimeEnvironment.getApplication())
        var retried = 0
        rule.setContent {
            PostPage(failure = MochiError.ServerError(500, "The server broke"), onRetry = { retried++ }) {
                Text("post")
            }
        }
        rule.onNodeWithText("The server broke").assertIsDisplayed()
        rule.onNodeWithText("Retry").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun `a post with no failure shows no retry`() {
        rule.setContent {
            PostPage(failure = null, onRetry = {}) { Text("post") }
        }
        rule.onNodeWithText("post").assertIsDisplayed()
        rule.onNodeWithText("Retry").assertDoesNotExist()
    }
}
