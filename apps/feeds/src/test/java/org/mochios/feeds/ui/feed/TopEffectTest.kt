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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.feeds.model.Post
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Where the feed's pager rests when its list of posts changes: on the reader's
 * post, or on the first one after a refresh they asked for. The pager is keyed
 * by post, as the feed's is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class TopEffectTest {

    @get:Rule
    val rule = createComposeRule()

    private var posts by mutableStateOf(emptyList<Post>())
    private var refreshing by mutableStateOf(false)
    private val top = mutableStateOf(false)
    private lateinit var pager: PagerState

    private fun list(vararg ids: String) = ids.map { id -> Post(id = id) }

    private fun show(vararg ids: String, page: Int = 0) {
        posts = list(*ids)
        rule.setContent {
            pager = rememberPagerState(initialPage = page, pageCount = { posts.size })
            TopEffect(pager, posts, refreshing, top)
            VerticalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                key = { index -> posts[index].id },
            ) { index ->
                Text(posts[index].id)
            }
        }
        rule.waitForIdle()
    }

    /** The post the pager rests on. */
    private fun reading(): String {
        rule.waitForIdle()
        return posts[pager.currentPage].id
    }

    /** What the refresh button and the pull both do before the list reloads. */
    private fun ask() {
        top.value = true
        refreshing = true
        rule.waitForIdle()
    }

    private fun turn(page: Int) {
        rule.runOnIdle { pager.requestScrollToPage(page) }
        rule.waitForIdle()
    }

    @Test
    fun `posts arriving above the reader leave them on their post`() {
        show("a", "b", "c", page = 1)
        posts = list("x", "y", "a", "b", "c")
        assertEquals("b", reading())
    }

    @Test
    fun `posts arriving above the first leave the reader on it`() {
        // The old list is long enough to have a post at the page the reader's moves to.
        show("a", "b", "c")
        posts = list("x", "y", "a", "b", "c")
        assertEquals("a", reading())
    }

    @Test
    fun `a post removed above the reader leaves them on theirs`() {
        show("a", "b", "c", "d", "e", page = 3)
        posts = list("a", "c", "d", "e")
        assertEquals("d", reading())
    }

    @Test
    fun `a refresh the reader asked for lands on the first of the new posts`() {
        show("a", "b", "c")
        ask()
        posts = list("x", "y", "a", "b", "c")
        refreshing = false
        assertEquals("x", reading())
        rule.onNodeWithText("x").assertIsDisplayed()
        assertFalse(top.value)
    }

    @Test
    fun `the request waits for the refresh's own list, past one that lands meanwhile`() {
        show("a", "b", "c")
        ask()
        // A post is deleted elsewhere while the refresh is out.
        posts = list("a", "c")
        rule.waitForIdle()
        assertTrue(top.value)
        posts = list("y", "x", "a", "c")
        refreshing = false
        assertEquals("y", reading())
    }

    @Test
    fun `a refresh that changes nothing spends the request, so a later change does not move the reader`() {
        show("a", "b", "c", page = 1)
        ask()
        refreshing = false
        assertEquals("a", reading())
        assertFalse(top.value)
        turn(2)
        posts = list("x", "a", "b", "c")
        assertEquals("c", reading())
    }
}
