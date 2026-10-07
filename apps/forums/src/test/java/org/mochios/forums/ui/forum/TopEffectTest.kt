// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.forum

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.forums.model.Post
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Where the forum's list rests when its posts are replaced: on the reader's
 * post, or on the first one after a refresh they asked for. The list is keyed
 * by post, as the forum's is, and runs to more than a screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class TopEffectTest {

    @get:Rule
    val rule = createComposeRule()

    private val old = (1..20).map { number -> Post(id = "p$number") }
    private var posts by mutableStateOf(old)
    private var refreshing by mutableStateOf(false)
    private val top = mutableStateOf(false)
    private lateinit var list: LazyListState

    private fun show() {
        rule.setContent {
            list = rememberLazyListState()
            TopEffect(list, posts, refreshing, top)
            LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                items(posts, key = { post -> post.id }) { post ->
                    Text(post.id, Modifier.height(100.dp))
                }
            }
        }
        rule.waitForIdle()
    }

    /** The post at the head of the list. */
    private fun head(): String {
        rule.waitForIdle()
        return posts[list.firstVisibleItemIndex].id
    }

    /** What the refresh button and the pull both do before the list reloads. */
    private fun ask() {
        top.value = true
        refreshing = true
        rule.waitForIdle()
    }

    private fun scroll(index: Int) {
        rule.runOnIdle { list.requestScrollToItem(index) }
        rule.waitForIdle()
    }

    @Test
    fun `new posts above the first stay out of sight until a refresh is asked for`() {
        show()
        posts = listOf(Post(id = "new")) + old
        assertEquals("p1", head())
    }

    @Test
    fun `a refresh the reader asked for lands on the first of the new posts`() {
        show()
        ask()
        posts = listOf(Post(id = "new")) + old
        refreshing = false
        assertEquals("new", head())
        rule.onNodeWithText("new").assertIsDisplayed()
        assertFalse(top.value)
    }

    @Test
    fun `a refresh that changes nothing spends the request, so a later change does not move the reader`() {
        show()
        scroll(5)
        ask()
        refreshing = false
        assertEquals("p1", head())
        assertFalse(top.value)
        scroll(5)
        posts = listOf(Post(id = "new")) + old
        assertEquals("p6", head())
    }
}
