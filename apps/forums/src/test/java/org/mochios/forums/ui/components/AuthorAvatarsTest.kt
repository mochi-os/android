// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.forums.model.ForumComment
import org.mochios.forums.model.Post

/**
 * An author's avatar route names one of their posts or comments, so building
 * each row's own address had the image cache fetch the same picture once per
 * row: one address per author instead, from a row every reader may load.
 */
class AuthorAvatarsTest {
    private fun comment(id: String, member: String, status: String = "approved", vararg children: ForumComment) =
        ForumComment(id = id, forum = "f1", post = "p1", member = member, status = status, children = children.toList())

    @Test
    fun `the post comes first and each commenter gets their first comment`() {
        val found = authorAvatars(
            listOf(Post(id = "p1", member = "ann", status = "approved")),
            { "f1" },
            listOf(comment("c1", "ann"), comment("c2", "bob", "approved", comment("c3", "cat"))),
            "f1",
        )
        assertEquals(
            mapOf(
                "ann" to "/forums/f1/-/p1/asset/avatar",
                "bob" to "/forums/f1/-/p1/c2/asset/avatar",
                "cat" to "/forums/f1/-/p1/c3/asset/avatar",
            ),
            found,
        )
    }

    @Test
    fun `a pending or removed row never stands in for its author`() {
        val found = authorAvatars(
            listOf(Post(id = "p1", member = "ann", status = "pending")),
            { "f1" },
            listOf(comment("c1", "ann", "removed"), comment("c2", "ann"), comment("c3", "bob", "pending")),
            "f1",
        )
        assertEquals(mapOf("ann" to "/forums/f1/-/p1/c2/asset/avatar"), found)
    }

    @Test
    fun `each post in a list is addressed under the forum its card names`() {
        val posts = listOf(
            Post(id = "p1", forum = "x", member = "ann"),
            Post(id = "p2", forum = "y", member = "bob"),
            Post(id = "p3", forum = "y", member = "ann"),
        )
        assertEquals(
            mapOf("ann" to "/forums/x/-/p1/asset/avatar", "bob" to "/forums/y/-/p2/asset/avatar"),
            authorAvatars(posts, { it.forum }),
        )
    }
}
