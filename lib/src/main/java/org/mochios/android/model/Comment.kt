// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.model

import com.google.gson.annotations.SerializedName

data class Comment(
    val id: String,
    val parent: String = "",
    // Gson instantiates via Unsafe, bypassing Kotlin defaults, so an absent
    // non-null field arrives as null and crashes on access. Feeds sends `user`,
    // not `author`; read identity via [authorId].
    val author: String? = null,
    val user: String? = null,
    val name: String = "",
    val body: String = "",
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
    // never anchored. Nullable for the Gson reason above.
    val attachment: String? = null,
    @SerializedName("attachment_name") val attachmentName: String? = null,
    @SerializedName("attachment_caption") val attachmentCaption: String? = null,
) {
    /** The anchored attachment's id, or "" when the comment is not about one. */
    val anchor: String get() = attachment.orEmpty()

    /** Returns body text — projects uses 'content', feeds uses 'body'. */
    val text: String get() = content.orEmpty().ifBlank { body }

    /** Commenter's entity id, regardless of which field the module populates. */
    val authorId: String get() = author.orEmpty().ifBlank { user.orEmpty() }

    /** Plain-text source for editing: markdown when present, else the body. */
    val markdownSource: String get() = bodyMarkdown.orEmpty().ifBlank { body }
}
