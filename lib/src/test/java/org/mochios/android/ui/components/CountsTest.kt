// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.NumberFormat
import org.mochios.android.i18n.UserPreferences
import org.mochios.android.model.ReactionCount
import org.mochios.android.model.ReactionType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Counts in the shared components are written as the user writes numbers,
 * as every other count is: these used to be written raw, ignoring the
 * grouping a large count takes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class CountsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun dotted(content: @Composable () -> Unit) {
        rule.setContent {
            CompositionLocalProvider(
                LocalFormat provides Format(UserPreferences(numberFormat = NumberFormat.EUROPEAN_DOT_COMMA)),
            ) { content() }
        }
        rule.waitForIdle()
    }

    @Test
    fun `a reaction's count is grouped`() {
        dotted {
            ReactionBar(
                reactions = listOf(ReactionCount(ReactionType.entries.first(), 12345, false)),
                onReact = {},
                onRemoveReaction = {},
            )
        }
        rule.onNodeWithText("12.345", substring = true).assertExists()
    }

    @Test
    fun `the hidden reactions' count is grouped`() {
        val types = ReactionType.entries
        dotted {
            ReactionBar(
                reactions = listOf(ReactionCount(types[0], 2, false), ReactionCount(types[1], 4321, false)),
                onReact = {},
                onRemoveReaction = {},
                maxVisible = 1,
            )
        }
        rule.onNodeWithText("+4.321").assertExists()
    }

    @Test
    fun `a post's tag count is grouped`() {
        dotted { PostTagsButton(tags = List(1200) { index -> TagItem(id = "t$index", label = "tag$index") }) }
        rule.onNodeWithText("1.200").assertExists()
    }

    @Test
    fun `a saved post's tag count is grouped`() {
        dotted { SavedPostFooter(tagCount = 1200, tagsLabel = "Tags", unsaveLabel = "Unsave", onUnsave = {}) }
        rule.onNodeWithText("1.200").assertExists()
    }

    @Test
    fun `a drawer row's unread count is grouped`() {
        dotted {
            MochiListDrawer(
                drawerState = rememberDrawerState(DrawerValue.Open),
                items = listOf(DrawerItem(id = "c1", title = "One", unread = 12345)),
                selectedId = null,
                onItemClick = {},
            ) {}
        }
        rule.onNodeWithText("12.345").assertExists()
    }
}
