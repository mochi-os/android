// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.`object`

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.model.Comment
import org.mochios.android.ui.components.CommentDraft
import org.mochios.android.ui.components.CommentDrafts
import org.mochios.projects.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The Comments tab leaving and coming back, as it does when the ticket's sheet
 * is swiped shut and reopened or another tab is chosen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class CommentsTabDraftTest {

    @get:Rule
    val rule = createComposeRule()

    /** Outlives the tab, as the view model's does. */
    private val drafts = CommentDrafts()
    private var shown by mutableStateOf(true)
    private var target by mutableStateOf("ticket")
    private var sent: Triple<String, String?, List<Uri>>? = null

    private fun show(comments: List<Comment> = emptyList()) {
        rule.setContent {
            if (shown) {
                CommentsTab(
                    comments = comments,
                    projectId = "project",
                    drafts = drafts,
                    target = target,
                    onCreateComment = { text, reply, files -> sent = Triple(text, reply, files) },
                    resolveFileName = { "" },
                    onUpdateComment = { _, _ -> },
                    onDeleteComment = {},
                )
            }
        }
    }

    /** Takes the tab out of the screen and puts it back. */
    private fun reopen() {
        shown = false
        rule.waitForIdle()
        shown = true
        rule.waitForIdle()
    }

    @Test
    fun `an unsent comment is still there when the tab comes back`() {
        show()
        rule.onNode(hasSetTextAction()).performTextInput("half a thought")
        reopen()
        rule.onNode(hasSetTextAction()).assertTextContains("half a thought")
    }

    @Test
    fun `an unsent comment belongs to the ticket it was typed on`() {
        show()
        rule.onNode(hasSetTextAction()).performTextInput("about the first")
        target = "another"
        rule.waitForIdle()
        rule.onNode(hasSetTextAction() and hasText("about the first")).assertDoesNotExist()
        target = "ticket"
        rule.waitForIdle()
        rule.onNode(hasSetTextAction()).assertTextContains("about the first")
    }

    @Test
    fun `sending hands over the comment with its reply and leaves nothing behind`() {
        show()
        drafts.update("ticket") { it.copy(reply = "parent") }
        rule.onNode(hasSetTextAction()).performTextInput("an answer")
        val send = RuntimeEnvironment.getApplication().getString(R.string.projects_comment_send)
        rule.onNodeWithContentDescription(send).performClick()
        rule.waitForIdle()
        assertEquals(Triple("an answer", "parent", emptyList<Uri>()), sent)
        assertEquals(CommentDraft(), drafts.draft("ticket"))
    }

    @Test
    fun `an edit under way is still there when the tab comes back`() {
        show(listOf(Comment(id = "first", body = "as posted", authorName = "Ann")))
        drafts.edit("first", "as posted, reworded")
        rule.waitForIdle()
        rule.onNode(hasSetTextAction() and hasText("as posted, reworded")).performTextReplacement("reworded again")
        reopen()
        rule.onNode(hasSetTextAction() and hasText("reworded again")).assertExists()
        assertEquals("reworded again", drafts.edit("first"))
    }

    @Test
    fun `a comment nobody is editing shows no edit field`() {
        show(listOf(Comment(id = "first", body = "as posted", authorName = "Ann")))
        rule.onNode(hasSetTextAction() and hasText("as posted")).assertDoesNotExist()
        assertNull(drafts.edit("first"))
    }
}
