// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mochios.android.model.Comment
import org.mochios.feeds.model.Post

/**
 * A commenter's avatar route names one of their comments, so building each
 * comment's own address had the image cache fetch the same picture once per
 * comment: one address per commenter instead.
 */
class CommenterAvatarsTest {
    private fun by(id: String, user: String, vararg children: Comment) =
        Comment(id = id, user = user, children = children.toList())

    @Test
    fun `a post's commenters each get their first comment's address, replies included`() {
        val comments = listOf(by("c1", "ann", by("c2", "bob"), by("c3", "ann")), by("c4", "bob"))
        assertEquals(
            mapOf(
                "ann" to "/feeds/f1/-/p1/c1/asset/avatar",
                "bob" to "/feeds/f1/-/p1/c2/asset/avatar",
            ),
            commenterAvatars(comments, "f1", "p1"),
        )
    }

    @Test
    fun `a timeline shares one address per commenter across posts and feeds`() {
        val posts = listOf(
            Post(id = "p1", feed = "feed-a", feedFingerprint = "fpa", comments = listOf(by("c1", "ann"))),
            Post(id = "p2", feed = "feed-b", comments = listOf(by("c2", "ann"), by("c3", "bob"))),
            Post(id = "p3", comments = listOf(by("c4", "cat"))),
        )
        assertEquals(
            mapOf(
                "ann" to "/feeds/fpa/-/p1/c1/asset/avatar",
                "bob" to "/feeds/feed-b/-/p2/c3/asset/avatar",
                "cat" to "/feeds/fallback/-/p3/c4/asset/avatar",
            ),
            timelineAvatars(posts, "fallback"),
        )
    }

    // Post and comment ids reach the phone from the feed's owner; a slash or a
    // question mark left in one would send the image request, with the
    // session, to whatever action the path then names.
    @Test
    fun `an id a peer chose cannot point the avatar at another action`() {
        assertEquals("/feeds/f1/-/p1%3Fx%3D1/c1%23y/asset/avatar", commentAvatar("f1", "p1?x=1", "c1#y"))
        // The server decodes the path before it routes, so no escaping keeps a
        // slash from dividing it.
        assertNull(commentAvatar("f1", "../../people/-/x", "c1"))
        assertNull(commentAvatar("f1", "p1", "members/someone"))
    }

    // The URL resolves "." and ".." away, escaped or not: p1/../asset/avatar
    // would ask for the post's own asset/avatar.
    @Test
    fun `a comment whose id is a dot segment does not stand in, and the commenter's next one does`() {
        val comments = listOf(by("..", "ann"), by(".", "bob"), by("c3", "ann"))
        assertEquals(mapOf("ann" to "/feeds/f1/-/p1/c3/asset/avatar"), commenterAvatars(comments, "f1", "p1"))
    }

    @Test
    fun `a post whose id is a dot segment addresses no comment`() {
        assertEquals(emptyMap<String, String>(), commenterAvatars(listOf(by("c1", "ann")), "f1", ".."))
    }
}
