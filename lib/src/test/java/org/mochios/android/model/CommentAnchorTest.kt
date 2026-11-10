// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * An unanchored comment carries "" or omits the field, and the accessors
 * treat both alike.
 */
class CommentAnchorTest {
    private val gson = Gson()

    @Test
    fun `a field the server leaves out keeps its default, so the comment can be copied`() {
        // As feeds sends a comment: no created_string.
        val json = """{"id":"c1","parent":"","body":"hello","format":"text","created":1,"my_reaction":"",
            "reactions":[],"children":[],"attachments":[]}"""
        val comment = gson.fromJson(json, Comment::class.java)
        assertEquals("", comment.createdString)
        assertEquals("love", comment.copy(myReaction = "love").myReaction)
    }

    @Test
    fun `an anchored comment names its image and caption`() {
        val json = """{"id":"c1","body":"about the harbour","attachment":"a1",
            "attachment_name":"The harbour","attachment_caption":"The harbour"}"""
        val comment = gson.fromJson(json, Comment::class.java)
        assertEquals("a1", comment.anchor)
        assertEquals("The harbour", comment.attachmentCaption)
        assertEquals("The harbour", comment.attachmentName)
    }

    @Test
    fun `an uncaptioned anchor keeps its file name for the display name and an empty caption`() {
        val json = """{"id":"c1","body":"x","attachment":"a1","attachment_name":"IMG_4823.jpg","attachment_caption":""}"""
        val comment = gson.fromJson(json, Comment::class.java)
        assertEquals("a1", comment.anchor)
        assertEquals("IMG_4823.jpg", comment.attachmentName)
        assertEquals("", comment.attachmentCaption)
    }

    @Test
    fun `a comment from before anchors, with no fields at all, is simply unanchored`() {
        val comment = gson.fromJson("""{"id":"c1","body":"x"}""", Comment::class.java)
        assertEquals("", comment.anchor)
        assertEquals(null, comment.attachmentCaption)
    }

    @Test
    fun `an unanchored comment sends an empty anchor`() {
        val comment = gson.fromJson("""{"id":"c1","body":"x","attachment":"","attachment_name":"","attachment_caption":""}""", Comment::class.java)
        assertEquals("", comment.anchor)
    }
}
