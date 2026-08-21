// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.model

/**
 * Row from the staff app's `directory/search` proxy (`action_directory_search`
 * in `apps/staff/staff.star`).
 */
data class DirectorySearchResult(
    val id: String = "",
    val name: String = "",
)

/**
 * Response envelope for `directory/search`. Mirrors the inline `{results}`
 * shape in `action_directory_search`.
 */
data class DirectorySearchResponse(
    val results: List<DirectorySearchResult> = emptyList(),
)
