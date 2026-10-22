// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.post

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.android.model.Comment
import org.mochios.feeds.ui.component.countComments
import org.mochios.feeds.ui.component.threadRows

class AttachmentCommentsTest {
    private fun comment(id: String, anchor: String = "", vararg children: Comment) =
        Comment(id = id, attachment = anchor, children = children.toList())

    private val thread = listOf(
        comment("a", "img1", comment("a1"), comment("a2", "", comment("a2a"))),
        comment("b", "img2"),
        comment("c"),
        comment("d", "img1"),
    )

    @Test
    fun `the count is the whole subtree of the comments anchored to the image`() {
        assertEquals(5, anchoredCommentCount(thread, "img1")) // a, a1, a2, a2a, d
        assertEquals(1, anchoredCommentCount(thread, "img2"))
        assertEquals(0, anchoredCommentCount(thread, "img3"))
    }

    @Test
    fun `countComments counts replies at every depth`() {
        assertEquals(7, countComments(thread))
    }

    @Test
    fun `filtering on the anchor keeps a reply with its parent`() {
        val shown = threadRows(thread.filter { it.anchor == "img1" }).map { it.comment.id to it.depth }
        assertEquals(listOf("a" to 0, "a1" to 1, "a2" to 1, "a2a" to 2, "d" to 0), shown)
    }

    @Test
    fun `a collapsed comment keeps its row and leaves out its replies at every depth`() {
        val shown = threadRows(thread, setOf("a")).map { it.comment.id }
        assertEquals(listOf("a", "b", "c", "d"), shown)
        val nested = threadRows(thread, setOf("a2")).map { it.comment.id }
        assertEquals(listOf("a", "a1", "a2", "b", "c", "d"), nested)
    }

    @Test
    fun `each row knows its parent and how many replies sit under it`() {
        val rows = threadRows(thread).associateBy { it.comment.id }
        assertEquals(null, rows.getValue("a").parent)
        assertEquals("a", rows.getValue("a2").parent)
        assertEquals("a2", rows.getValue("a2a").parent)
        assertEquals(3, rows.getValue("a").replies)
        assertEquals(1, rows.getValue("a2").replies)
        assertEquals(0, rows.getValue("b").replies)
    }
}
