// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.model


data class WikiComment(
    val id: String = "",
    val wiki: String = "",
    val page: String = "",
    val parent: String = "",
    val author: String = "",
    val name: String = "",
    val body: String = "",
    val created: Long = 0,
    val edited: Long = 0,
    val children: List<WikiComment> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
)

data class CommentsResponse(
    val comments: List<WikiComment> = emptyList(),
    val count: Int = 0,
    /** Total matching threads, which exceeds [comments] when [truncated]. */
    val total: Int = 0,
    /**
     * The server capped the page (200 threads) and there are more. Web says so
     * under the list; without it a page past the cap silently loses its tail.
     */
    val truncated: Boolean = false,
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
