// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // Post and comment ids reach the phone from the forum's owner; a slash or a
    // question mark left in one would send the image request, with the
    // session, to whatever action the path then names.
    @Test
    fun `a post id a peer chose cannot point the avatar at another action`() {
        assertEquals("/forums/f1/-/p1%3Fx%3D1/asset/avatar", postAvatar("f1", "p1?x=1"))
        // The server decodes the path before it routes, so no escaping keeps a
        // slash from dividing it: members/<user> would reach the members route.
        assertNull(postAvatar("f1", "../../people/-/x"))
        assertNull(postAvatar("f1", "members/someone"))
    }

    @Test
    fun `a comment id a peer chose cannot point the avatar at another action`() {
        assertEquals("/forums/f1/-/p%3F/y%3Fz/asset/avatar", commentAvatar("f1", "p?", "y?z"))
        assertNull(commentAvatar("f1", "../p", "c1"))
        assertNull(commentAvatar("f1", "p1", "moderation/someone"))
    }

    // The URL resolves "." and ".." away, escaped or not: f1/-/../asset/avatar
    // would ask for another route's asset/avatar.
    @Test
    fun `a post whose id is a dot segment does not stand in, and its author's comment does`() {
        val found = authorAvatars(
            listOf(Post(id = "..", member = "ann", status = "approved")),
            { "f1" },
            listOf(comment("c1", "ann")),
            "f1",
        )
        assertEquals(mapOf("ann" to "/forums/f1/-/p1/c1/asset/avatar"), found)
    }

    @Test
    fun `a comment whose id is a dot segment does not stand in, and the commenter's next one does`() {
        val found = authorAvatars(emptyList(), { "f1" }, listOf(comment(".", "bob"), comment("c2", "bob")), "f1")
        assertEquals(mapOf("bob" to "/forums/f1/-/p1/c2/asset/avatar"), found)
    }
}
