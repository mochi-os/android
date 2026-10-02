// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.layout.ContentScale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.NumberFormat
import org.mochios.android.i18n.UserPreferences
import org.mochios.android.model.Attachment
import org.mochios.android.model.Comment
import org.mochios.feeds.model.MemoryData
import org.mochios.feeds.model.Post
import org.mochios.feeds.model.PostData
import org.mochios.feeds.model.Source
import org.mochios.feeds.ui.feed.GalleryTile
import org.mochios.feeds.ui.feed.PostByline
import org.mochios.feeds.ui.feed.PostCommentsPreview
import org.mochios.feeds.ui.settings.SourceCard
import org.mochios.feeds.ui.settings.SuggestedCredibilityDialog
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Counts inside sentences used to be written by String.format, which uses the
 * language's own digits (Arabic-Indic in Arabic) and no grouping. They are
 * written as the user writes numbers, digits 0 to 9, as the web writes them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ar")
class CountFormatTest {

    @get:Rule
    val rule = createComposeRule()

    private val european = Format(UserPreferences(numberFormat = NumberFormat.EUROPEAN_DOT_COMMA))

    private fun show(content: @Composable () -> Unit) {
        rule.setContent { CompositionLocalProvider(LocalFormat provides european, content = content) }
    }

    @Test
    fun `the comments a post's preview leaves out`() {
        show {
            PostCommentsPreview(
                post = Post(id = "p", comments = List(1003) { index -> Comment(id = "c$index", body = "c$index") }),
                fallbackFeedId = "f",
                onViewComments = {},
            )
        }
        rule.onNodeWithText("1.000", substring = true).assertExists()
    }

    @Test
    fun `the images a gallery has no room for`() {
        show {
            GalleryTile(
                attachment = Attachment(id = "a", name = "a.png", type = "image/png"),
                model = "https://example.org/a.png",
                contentScale = ContentScale.Crop,
                more = 1234,
                onClick = {},
            )
        }
        rule.onNodeWithText("+1.234").assertExists()
    }

    @Test
    fun `a source's polling interval`() {
        show { SourceCard(source = Source(id = "s", type = "rss", name = "News", interval = 3_600_000), onEdit = {}, onRemove = {}, onPoll = {}) }
        rule.onNodeWithText("1.000", substring = true).assertExists()
    }

    @Test
    fun `a memory's years`() {
        show { PostByline(post = Post(id = "p", feedName = "Feed", data = PostData(memory = MemoryData(yearsAgo = 3)))) }
        rule.onNodeWithText("قبل 3 أعوام", substring = true).assertExists()
    }

    @Test
    fun `a suggested credibility`() {
        show { SuggestedCredibilityDialog(suggested = 75, busy = false, onAccept = {}, onDismiss = {}) }
        rule.onNodeWithText("75", substring = true).assertExists()
    }
}
