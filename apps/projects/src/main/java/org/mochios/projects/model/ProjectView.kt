// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.model

data class ProjectView(
    val id: String = "",
    val name: String = "",
    val viewtype: String = "",
    val filter: String = "",
    val columns: String = "",
    val rows: String = "",
    val fields: String = "",
    val sort: String = "",
    val direction: String = "asc",
    val classes: List<String> = emptyList(),
    val rank: Int = 0,
    val border: String = ""
)
