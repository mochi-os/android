// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.model

/**
 * One entry of an object's history. [display] is what the server resolved
 * the entry to name; an older server sends none.
 */
data class Activity(
    val id: String = "",
    val user: String = "",
    val name: String = "",
    val action: String = "",
    val field: String = "",
    val oldvalue: String = "",
    val newvalue: String = "",
    val created: Long = 0,
    val display: ActivityDisplay? = null,
)

/**
 * What an activity entry names, as the server resolved it: [field] the name
 * of the field of the object's class it changed and [type] that field's type;
 * [old] and [new] an option's name, a person's name, an object's title or a
 * class's name. A side is null where the client formats the stored value by
 * the field's type.
 */
data class ActivityDisplay(
    val field: String? = null,
    val type: String? = null,
    val old: String? = null,
    val new: String? = null,
)
