// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.model

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
 * What the server resolved an entry to name. Each is absent where the client
 * formats the stored value itself, by the field's type.
 *
 * @property field The changed field's name, when it is a field of the object's class.
 * @property type That field's type.
 * @property old The old value as text: an option's name, a person's, a parent's
 *   or a link target's title, or a class's name.
 * @property new The new value, in the same way.
 */
data class ActivityDisplay(
    val field: String? = null,
    val type: String? = null,
    val old: String? = null,
    val new: String? = null,
)
