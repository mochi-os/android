// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Rows a relayed forum sends as its server wrote them, made fit for a list
 * keyed by id: a repeated key closes the app.
 */
class ForumRowsTest {

    private fun comment(id: String, vararg children: ForumComment) =
        ForumComment(id = id, children = children.toList())

    /** Each comment's id, depth first, with its depth. */
    private fun flat(comments: List<ForumComment>, depth: Int = 0): List<String> =
        comments.flatMap { comment -> listOf("$depth:${comment.id}") + flat(comment.children, depth + 1) }

    @Test
    fun `a post without an id, and each repeat of one, is left out`() {
        val posts = listOf(Post(id = "a"), Post(id = "b"), Post(id = "a", title = "again"), Post(id = ""), Post(id = "c"))
        val kept = unique(posts)
        assertEquals(listOf("a", "b", "c"), kept.map { post -> post.id })
        assertEquals("", kept.first().title)
    }

    @Test
    fun `a comment is kept once wherever it repeats in the thread, and none without an id`() {
        val comments = listOf(
            comment("c1", comment("c2"), comment("c1"), comment("")),
            comment("c2"),
            comment("", comment("c3")),
            comment("c4", comment("c5", comment("c4"))),
        )
        assertEquals(listOf("0:c1", "1:c2", "0:c4", "1:c5"), flat(thread(comments)))
    }

    @Test
    fun `a thread with nothing to drop is the same thread`() {
        val comments = listOf(comment("c1", comment("c2", comment("c3"))), comment("c4"))
        assertEquals(comments, thread(comments))
    }
}
