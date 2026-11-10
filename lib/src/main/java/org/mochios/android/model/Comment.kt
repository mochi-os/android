// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.model

import com.google.gson.annotations.SerializedName

// Every field has a default, so Kotlin gives the class a constructor with no
// arguments and Gson builds it through that, keeping the defaults for fields a
// server leaves out. Without one, Gson bypasses the defaults and an absent
// field arrives as null whatever its type: feeds sends no `created_string`, so
// `copy()` on one of its comments would throw.
data class Comment(
    val id: String = "",
    val parent: String = "",
    // Feeds sends `user`, not `author`; read identity via [authorId].
    val author: String? = null,
    val user: String? = null,
    @SerializedName("name") val authorName: String? = null,
    val body: String? = null,
    val content: String? = null,
    @SerializedName("body_markdown") val bodyMarkdown: String? = null,
    val format: String = "",
    val created: Long = 0,
    @SerializedName("created_string") val createdString: String = "",
    val edited: Long = 0,
    val children: List<Comment> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    @SerializedName("my_reaction") val myReaction: String = "",
    val reactions: List<Reaction> = emptyList(),
    // The post attachment this comment is about: id, display name (caption,
    // else file name), and caption alone. Empty when unanchored; replies are
    // never anchored.
    val attachment: String? = null,
    @SerializedName("attachment_name") val attachmentName: String? = null,
    @SerializedName("attachment_caption") val attachmentCaption: String? = null,
    // A comment from another network: the author's page there and the
    // comment's id there.
    val page: String? = null,
    val remote: String? = null,
) {
    /** The anchored attachment's id, or "" when the comment is not about one. */
    val anchor: String get() = attachment.orEmpty()

    /** Commenter's display name, or "" when the server omits it. */
    val name: String get() = authorName.orEmpty()

    /** Returns body text — projects uses 'content', feeds uses 'body'. */
    val text: String get() = content.orEmpty().ifBlank { body.orEmpty() }

    /** Commenter's entity id, regardless of which field the module populates. */
    val authorId: String get() = author.orEmpty().ifBlank { user.orEmpty() }

    /** Plain-text source for editing: markdown when present, else the body. */
    val markdownSource: String get() = bodyMarkdown.orEmpty().ifBlank { body.orEmpty() }
}
