// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.model

import com.google.gson.annotations.SerializedName

data class WikiComment(
    val id: String = "",
    val wiki: String = "",
    val page: String = "",
    val parent: String = "",
    val author: String = "",
    val name: String = "",
    val body: String = "",
    @SerializedName("body_markdown")
    val bodyMarkdown: String = "",
    val created: Long = 0,
    val edited: Long = 0,
    val children: List<WikiComment> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
)

data class CommentsResponse(
    val comments: List<WikiComment> = emptyList(),
    val count: Int = 0,
)

data class CommentCreateResponse(
    val id: String = "",
    val wiki: String = "",
    val page: String = "",
    val parent: String = "",
    val author: String = "",
    val name: String = "",
    val body: String = "",
    val created: Long = 0,
)

data class CommentEditResponse(
    val id: String = "",
    val wiki: String = "",
    val page: String = "",
    val body: String = "",
    val edited: Long = 0,
)

data class CommentDeleteResponse(
    val ok: Boolean = false,
)
