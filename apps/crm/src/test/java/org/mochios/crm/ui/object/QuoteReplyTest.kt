// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.`object`

import android.text.Selection
import android.text.Spannable
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.R as MochiR
import org.mochios.android.model.Comment
import org.mochios.android.ui.components.ClickableLinkTextView
import org.mochios.android.ui.components.CommentDraft
import org.mochios.android.ui.components.CommentDrafts
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Reply quotes the text selected on the page into the composer, as the web's
 * does with `window.getSelection()`: whichever comment holds the selection,
 * replacing an earlier Reply's quote rather than stacking a second, from the
 * Reply icon and from the selection's own menu.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class QuoteReplyTest {

    @get:Rule
    val rule = createComposeRule()

    private val drafts = CommentDrafts()
    private var root: View? = null

    private fun bodies(view: View): List<ClickableLinkTextView> = when (view) {
        is ClickableLinkTextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { bodies(view.getChildAt(it)) }
        else -> emptyList()
    }

    /** The tab with two comments, "The quick brown fox" and "Jumps over", and the first one's body. */
    private fun show(): ClickableLinkTextView {
        rule.setContent {
            root = LocalView.current
            CommentsTab(
                comments = listOf(
                    Comment(id = "c1", body = "The quick brown fox", authorName = "Ann"),
                    Comment(id = "c2", body = "Jumps over", authorName = "Bob"),
                ),
                currentUserId = null,
                crmId = "crm",
                drafts = drafts,
                target = "ticket",
                onCreateComment = { _, _, _ -> },
                resolveFileName = { "" },
                onUpdateComment = { _, _ -> },
                onDeleteComment = {},
            )
        }
        rule.waitForIdle()
        return bodies(root!!).single { body -> body.text.startsWith("The") }
    }

    /** Taps Reply on the comment at [index]. */
    private fun reply(index: Int) {
        rule.onAllNodesWithContentDescription(RuntimeEnvironment.getApplication().getString(MochiR.string.comment_reply))[index]
            .performClick()
        rule.waitForIdle()
    }

    @Test
    fun `Reply quotes the text selected in another comment ahead of the draft`() {
        val first = show()
        drafts.update("ticket") { it.copy(text = "my words") }
        rule.runOnUiThread { Selection.setSelection(first.text as Spannable, 4, 15) }
        reply(1)
        assertEquals(
            "THE POINT: the selection is quoted wherever it is",
            CommentDraft(text = "> quick brown\n\nmy words", reply = "c2", quote = "> quick brown\n\n"),
            drafts.draft("ticket"),
        )
    }

    @Test
    fun `a second Reply replaces the first's quote`() {
        val action = show().selectionAction!!
        drafts.update("ticket") { it.copy(text = "my words") }
        rule.runOnUiThread { action.onSelected("quick brown") }
        rule.runOnUiThread { action.onSelected("quick brown") }
        rule.waitForIdle()
        assertEquals("THE POINT: quoted once", "> quick brown\n\nmy words", drafts.draft("ticket").text)
    }

    @Test
    fun `Reply in the selection's menu quotes it too`() {
        val action = show().selectionAction
        assertNotNull("the body's selection menu offers Reply", action)
        rule.runOnUiThread { action!!.onSelected("quick brown") }
        rule.waitForIdle()
        assertEquals(CommentDraft(text = "> quick brown\n\n", reply = "c1", quote = "> quick brown\n\n"), drafts.draft("ticket"))
    }
}
