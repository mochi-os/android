// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.post

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.android.model.Comment
import org.mochios.feeds.ui.component.countComments
import org.mochios.feeds.ui.component.flattenComments

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
        val shown = flattenComments(thread.filter { it.anchor == "img1" }, 0).map { it.first.id to it.second }
        assertEquals(listOf("a" to 0, "a1" to 1, "a2" to 1, "a2a" to 2, "d" to 0), shown)
    }
}
