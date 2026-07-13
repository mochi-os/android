// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.model

data class SearchResult(
    val page: String = "",
    val title: String = "",
    val excerpt: String = "",
    val updated: Long = 0,
)

data class SearchResponse(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
)
