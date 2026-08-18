// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A forum comment anchored to one of its post's attachments carries the anchor
 * and caption on the wire; older or unanchored comments carry "" or nothing.
 * The lightbox count for an image is every comment in the anchored trees.
 */
class ForumCommentAnchorTest {
    private val gson = Gson()

    @Test
    fun `an anchored comment names its image and caption`() {
        val json = """{"id":"c1","body":"x","attachment":"a1","attachment_name":"The harbour","attachment_caption":"The harbour"}"""
        val comment = gson.fromJson(json, ForumComment::class.java)
        assertEquals("a1", comment.anchor)
        assertEquals("The harbour", comment.attachmentCaption)
    }

    @Test
    fun `absent and empty anchor fields both read as unanchored`() {
        assertEquals("", gson.fromJson("""{"id":"c1","body":"x"}""", ForumComment::class.java).anchor)
        assertEquals("", gson.fromJson("""{"id":"c1","body":"x","attachment":""}""", ForumComment::class.java).anchor)
    }

    @Test
    fun `countComments counts replies at every depth`() {
        val thread = listOf(
            ForumComment(id = "a", attachment = "img1", children = listOf(
                ForumComment(id = "a1"),
                ForumComment(id = "a2", children = listOf(ForumComment(id = "a2a"))),
            )),
            ForumComment(id = "b"),
        )
        assertEquals(5, countComments(thread))
        assertEquals(4, countComments(thread.filter { it.anchor == "img1" }))
    }
}
