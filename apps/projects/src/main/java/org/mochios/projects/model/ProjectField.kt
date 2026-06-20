// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.model

data class ProjectField(
    val id: String = "",
    val name: String = "",
    val fieldtype: String = "",
    val flags: String = "",
    val multi: Int = 0,
    val rank: Int = 0,
    val card: Int = 0,
    val position: String = "",
    val rows: Int = 0,
    val min: String = "",
    val max: String = "",
    val pattern: String = "",
    val minlength: Int = 0,
    val maxlength: Int = 0
) {
    val isMulti get() = multi != 0
    val showOnCard get() = card != 0
    val isRequired get() = "required" in flags
    val isReadonly get() = "readonly" in flags
    val isSortable get() = "sort" in flags
    val isFilterable get() = "filter" in flags
}
