// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.feeds.model.Post
import org.mochios.feeds.model.Tag
import org.mochios.feeds.ui.feed.PostActionBar
import org.mochios.feeds.ui.feed.TagFilter
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A post's tags offer to filter the posts by one of them, as on the web, and
 * the filter shows above the posts with the way back to every post.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TagFilterScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `a tag in a post's tags menu filters the posts by it`() {
        val picked = mutableListOf<String>()
        rule.setContent {
            PostActionBar(
                post = Post(id = "p1", tags = listOf(Tag(id = "t1", label = "harbour"))),
                isSaved = false, canManage = false,
                onReact = {}, onComments = {}, onToggleSave = {}, onAddTag = {}, onAdjustInterest = { _, _ -> },
                onEdit = {}, onDelete = {},
                onFilterTag = { label -> picked.add(label) },
            )
        }
        rule.onNodeWithContentDescription("Tags").performClick()
        rule.onNodeWithText("#harbour").performClick()
        assertEquals(listOf("harbour"), picked)
    }

    @Test
    fun `the filter names its tag and clears`() {
        var cleared = 0
        rule.setContent { TagFilter(tag = "harbour", onClear = { cleared++ }) }
        rule.onNodeWithText("Filtered by tag:").assertIsDisplayed()
        rule.onNodeWithText("harbour").assertIsDisplayed()
        rule.onNodeWithContentDescription("Clear").performClick()
        assertEquals(1, cleared)
    }
}
