// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.model

data class Tag(
    val tag: String = "",
    val count: Int = 0,
)

data class TagsResponse(
    val tags: List<Tag> = emptyList(),
)

data class TagPage(
    val page: String = "",
    val title: String = "",
    val updated: Long = 0,
)

data class TagPagesResponse(
    val tag: String = "",
    val pages: List<TagPage> = emptyList(),
)

data class TagAddResponse(
    val ok: Boolean = false,
    val added: Boolean = false,
)

data class TagRemoveResponse(
    val ok: Boolean = false,
)
