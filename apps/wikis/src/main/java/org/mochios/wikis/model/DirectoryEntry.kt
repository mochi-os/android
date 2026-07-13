// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.model

data class DirectoryEntry(
    val id: String = "",
    val name: String = "",
    val fingerprint: String = "",
    val location: String? = null,
)

data class DirectorySearchResponse(
    val results: List<DirectoryEntry> = emptyList(),
)
