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
