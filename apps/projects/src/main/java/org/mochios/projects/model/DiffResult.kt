// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.model

data class DiffResult(
    val diff: String = "",
    val stats: DiffStats = DiffStats()
)

data class DiffStats(
    val files: Int = 0,
    val additions: Int = 0,
    val deletions: Int = 0
)
