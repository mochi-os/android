// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Folding, opening one comment, and sharing it, as the web's comment threads do. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CommentThreadTest {

    @get:Rule
    val rule = createComposeRule()

    private data class Node(val id: String, val children: List<Node> = emptyList())

    private fun node(id: String, vararg children: Node) = Node(id, children.toList())

    private val thread = listOf(
        node("a", node("a1"), node("a2", node("a2a"))),
        node("b"),
    )

    private fun rows(collapsed: Set<String> = emptySet()) =
        commentRows(thread, { it.id }, { it.children }, collapsed)

    @Test
    fun `rows come in reading order with their depth, parent and replies`() {
        val rows = rows()
        assertEquals(listOf("a", "a1", "a2", "a2a", "b"), rows.map { it.comment.id })
        assertEquals(listOf(0, 1, 1, 2, 0), rows.map { it.depth })
        assertEquals(listOf(null, "a", "a", "a2", null), rows.map { it.parent })
        assertEquals(listOf(3, 0, 1, 0, 0), rows.map { it.replies })
    }

    @Test
    fun `a folded comment keeps its row and leaves out its replies at every depth`() {
        assertEquals(listOf("a", "b"), rows(setOf("a")).map { it.comment.id })
        assertEquals(listOf("a", "a1", "a2", "b"), rows(setOf("a2")).map { it.comment.id })
        // Its count still says what is folded away.
        assertEquals(3, rows(setOf("a")).first().replies)
    }

    @Test
    fun `a comment is found at any depth, and a missing one is not`() {
        assertEquals("a2a", findInThread(thread, "a2a", { it.id }, { it.children })?.id)
        assertEquals(listOf("a2a"), findInThread(thread, "a2", { it.id }, { it.children })?.children?.map { it.id })
        assertNull(findInThread(thread, "gone", { it.id }, { it.children }))
    }

    @Test
    fun `folding toggles, and folding twice from a reply's line keeps it folded`() {
        val collapsed = CollapsedComments()
        collapsed.toggle("a")
        assertTrue("a" in collapsed)
        collapsed.toggle("a")
        assertFalse("a" in collapsed)
        collapsed.collapse("a")
        collapsed.collapse("a")
        assertEquals(setOf("a"), collapsed.ids)
    }

    private var toggled = 0
    private var parentFolded = 0
    private var shared = 0

    private fun show(
        depth: Int = 0,
        replies: Int = 0,
        collapsed: Boolean = false,
        foldable: Boolean = replies > 0,
        parent: Boolean = false,
        badge: String? = null,
    ) {
        rule.setContent {
            CommentItem(
                name = "Ann",
                body = "Hello",
                created = 0,
                depth = depth,
                seed = "ann",
                attachments = emptyList(),
                attachmentUrl = { "" },
                attachmentThumbnailUrl = { "" },
                collapsed = collapsed,
                replies = replies,
                onToggleCollapse = if (foldable) ({ toggled++ }) else null,
                onCollapseParent = if (parent) ({ parentFolded++ }) else null,
                badge = badge,
            ) {
                CommentActions(onReply = {}, onShare = { shared++ })
            }
        }
        rule.waitForIdle()
    }

    private val foldsParent = SemanticsMatcher("folds the comment replied to") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == "Collapse"
    }

    @Test
    fun `a tap on the header line folds a comment with replies`() {
        show(replies = 2)
        rule.onNodeWithText("Ann").performClick()
        assertEquals(1, toggled)
    }

    @Test
    fun `a comment without replies has nothing to fold`() {
        show(replies = 0)
        rule.onAllNodes(hasClickAction() and SemanticsMatcher("label") {
            it.config.getOrNull(SemanticsActions.OnClick)?.label != null
        }).assertCountEquals(0)
    }

    @Test
    fun `folded, the row says how many replies wait, drops its actions, and a tap opens it`() {
        show(replies = 3, collapsed = true)
        rule.onNodeWithText("+3 more replies", useUnmergedTree = true).assertExists()
        rule.onNodeWithContentDescription("Reply").assertDoesNotExist()
        rule.onNodeWithText("Ann").performClick()
        assertEquals(1, toggled)
    }

    @Test
    fun `the line beside a reply folds the comment it answers`() {
        show(depth = 1, parent = true)
        rule.onAllNodes(foldsParent).assertCountEquals(1)
        rule.onNode(foldsParent).performClick()
        assertEquals(1, parentFolded)
        assertEquals(0, toggled)
    }

    @Test
    fun `a top-level comment has no line to fold from`() {
        show(depth = 0, parent = false)
        rule.onAllNodes(foldsParent).assertCountEquals(0)
    }

    @Test
    fun `a badge follows the name`() {
        show(badge = "OP")
        rule.onNodeWithText("OP", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `Share sits among the comment's actions`() {
        show()
        rule.onNodeWithContentDescription("Share").performClick()
        assertEquals(1, shared)
    }
}
