// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.model

/**
 * One result row from the staff directory search proxy
 * (`directory/search`). Sandboxed apps can't call
 * `/people/-/users/search` directly (their JWT is scoped to staff), so the
 * staff app re-exposes `mochi.directory.*` via a same-app action — see
 * `action_directory_search` in `apps/staff/staff.star`.
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
