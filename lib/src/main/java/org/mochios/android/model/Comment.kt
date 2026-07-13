// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.model

import com.google.gson.annotations.SerializedName

data class Comment(
    val id: String,
    val parent: String = "",
    // Nullable because the field is often absent from the JSON, and Gson
    // instantiates models via Unsafe (bypassing Kotlin defaults) — so an absent
    // non-null field would arrive as null and crash on access. Feeds also
    // identify the commenter with `user` rather than `author`; read identity
    // via [authorId].
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
    val reactions: List<Reaction> = emptyList()
) {
    /** Returns body text — projects uses 'content', feeds uses 'body'. */
    val text: String get() = content.orEmpty().ifBlank { body }

    /** Commenter's entity id, regardless of which field the module populates. */
    val authorId: String get() = author.orEmpty().ifBlank { user.orEmpty() }

    /** Plain-text source for editing: markdown when present, else the body. */
    val markdownSource: String get() = bodyMarkdown.orEmpty().ifBlank { body }
}
