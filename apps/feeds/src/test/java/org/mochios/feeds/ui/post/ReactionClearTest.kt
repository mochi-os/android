// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.post

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.feeds.model.Permissions
import org.mochios.feeds.model.Post
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The server's react is no toggle: a reaction replaces the reader's and only
 * "none" takes it back. Clearing a reaction on the post screen, and in the
 * article's sheet that shares its content, sends that, as the feed card and
 * comments do.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReactionClearTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `clearing the reader's reaction to the post sends none`() {
        val sent = mutableListOf<String>()
        rule.setContent {
            PostContent(
                post = Post(id = "p1", feed = "f1", myReaction = "like"),
                tags = emptyList(),
                permissions = Permissions(),
                feedId = "f1",
                onReact = { reaction -> sent += reaction },
                onAddTag = {},
                onAdjustInterest = { _, _ -> },
            )
        }
        rule.onNodeWithText("👍").performClick()
        rule.onNodeWithText("Clear").performClick()
        assertEquals("THE POINT: the reaction held is not sent again", listOf("none"), sent)
    }
}
