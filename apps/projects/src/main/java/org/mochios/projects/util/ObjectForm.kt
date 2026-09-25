// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.util

import org.mochios.projects.model.FieldOption
import org.mochios.projects.model.ProjectField

/**
 * The values a create form holds once it is on a class: those of [previous]
 * the class takes, then the [preset] ones a board column's "+" hands over,
 * then the first option of any required enumerated field still blank, as the
 * web dialog seeds it. [column] is a board's column field: an object blank
 * there would land in "Unassigned", so it takes its first option too.
 */
fun formValues(
    fields: List<ProjectField>,
    options: Map<String, List<FieldOption>>,
    previous: Map<String, String>,
    preset: Map<String, String>,
    column: String = "",
): Map<String, String> {
    val values = mutableMapOf<String, String>()
    for ((id, value) in previous) {
        if (usable(fields, options, id, value)) values[id] = value
    }
    for ((id, value) in preset) {
        if (usable(fields, options, id, value)) values[id] = value
    }
    for (field in fields) {
        val seeded = (field.isRequired && field.fieldtype == "enumerated") || field.id == column
        if (seeded && values[field.id].isNullOrBlank()) {
            options[field.id].orEmpty().minByOrNull { option -> option.rank }
                ?.let { option -> values[field.id] = option.id }
        }
    }
    return values
}

/**
 * Whether [fieldId] on this class takes [value]: the field exists, and an
 * enumerated value is one of its own options - a mixed-class board can hand
 * over another class's.
 */
private fun usable(
    fields: List<ProjectField>,
    options: Map<String, List<FieldOption>>,
    fieldId: String,
    value: String,
): Boolean {
    if (value.isBlank()) return false
    val field = fields.firstOrNull { field -> field.id == fieldId } ?: return false
    if (field.fieldtype != "enumerated") return true
    return options[fieldId].orEmpty().any { option -> option.id == value }
}

/** Whether every field flagged required holds a value. */
fun requiredFilled(fields: List<ProjectField>, values: Map<String, String>): Boolean =
    fields.none { field -> field.isRequired && values[field.id].isNullOrBlank() }

/** Required enumerated fields with no option to pick, which no form can fill. */
fun unsatisfiable(
    fields: List<ProjectField>,
    options: Map<String, List<FieldOption>>,
): List<ProjectField> = fields.filter { field ->
    field.isRequired && field.fieldtype == "enumerated" && options[field.id].orEmpty().isEmpty()
}
