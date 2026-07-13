// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.model

data class Change(
    val id: String = "",
    val slug: String = "",
    val title: String = "",
    val author: String = "",
    val name: String = "",
    val created: Long = 0,
    val version: Int = 0,
    val comment: String = "",
)

data class ChangesResponse(
    val changes: List<Change> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
)
