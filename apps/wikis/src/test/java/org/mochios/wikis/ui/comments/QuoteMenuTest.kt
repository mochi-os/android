// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.comments

import android.text.Selection
import android.text.Spannable
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
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
import org.mochios.android.ui.components.ClickableLinkTextView
import org.mochios.android.ui.components.SelectionAction
import org.mochios.wikis.model.WikiComment
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A comment's body offers Reply in its text selection's menu, which quotes the
 * selection as the Reply icon does: the selection's handles hang over the
 * actions row, and take a tap meant for the icon.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuoteMenuTest {

    @get:Rule
    val rule = createComposeRule()

    private fun bodies(view: View): List<ClickableLinkTextView> = when (view) {
        is ClickableLinkTextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { bodies(view.getChildAt(it)) }
        else -> emptyList()
    }

    private val started = mutableListOf<Pair<String, String?>>()

    /** The action the comment's body offers in its selection menu. */
    private fun offered(): SelectionAction? {
        var root: View? = null
        rule.setContent {
            root = LocalView.current
            WikiCommentThread(
                comment = WikiComment(id = "c1", author = "bob", name = "Bob", body = "The quick brown fox"),
                currentUserId = null,
                isOwner = false,
                onStartReply = { id, selected -> started += id to selected },
                onEdit = null,
                onDelete = null,
            )
        }
        rule.waitForIdle()
        return bodies(root!!).single().selectionAction
    }

    @Test
    fun `the selection's menu offers Reply`() {
        val action = offered()
        assertNotNull("the body's selection menu offers an action", action)
        assertEquals("Reply", action!!.label)
    }

    @Test
    fun `the Reply icon quotes text selected in another comment, as the web reads the page's selection`() {
        var root: View? = null
        rule.setContent {
            root = LocalView.current
            Column {
                WikiCommentThread(
                    comment = WikiComment(id = "c1", author = "bob", name = "Bob", body = "The quick brown fox"),
                    currentUserId = null,
                    isOwner = false,
                    onStartReply = { id, selected -> started += id to selected },
                    onEdit = null,
                    onDelete = null,
                )
                WikiCommentThread(
                    comment = WikiComment(id = "c2", author = "ann", name = "Ann", body = "Jumps over"),
                    currentUserId = null,
                    isOwner = false,
                    onStartReply = { id, selected -> started += id to selected },
                    onEdit = null,
                    onDelete = null,
                )
            }
        }
        rule.waitForIdle()
        val first = bodies(root!!).single { body -> body.text.startsWith("The") }
        rule.runOnUiThread { Selection.setSelection(first.text as Spannable, 4, 15) }
        rule.onAllNodesWithContentDescription(RuntimeEnvironment.getApplication().getString(MochiR.string.comment_reply))[1]
            .performClick()
        rule.waitForIdle()
        assertEquals("THE POINT: the second comment's Reply quotes the first's selection", listOf("c2" to "quick brown"), started)
    }

    @Test
    fun `its Reply starts a reply to the comment quoting the selection`() {
        val action = offered()
        assertNotNull("the body's selection menu offers an action", action)
        rule.runOnUiThread { action!!.onSelected("quick brown") }
        assertEquals(listOf("c1" to "quick brown"), started)
    }
}
