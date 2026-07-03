// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.model

import com.google.gson.annotations.SerializedName

data class Comment(
    val id: String,
    val parent: String = "",
    val author: String = "",
    val name: String = "",
    val body: String = "",
    val content: String = "",
    @SerializedName("body_markdown") val bodyMarkdown: String = "",
    val created: Long = 0,
    @SerializedName("created_string") val createdString: String = "",
    val edited: Long = 0,
    val children: List<Comment> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    @SerializedName("my_reaction") val myReaction: String = "",
    val reactions: List<Reaction> = emptyList()
) {
    /** Returns body text — projects uses 'content', feeds uses 'body' */
    val text: String get() = content.ifBlank { body }
}
