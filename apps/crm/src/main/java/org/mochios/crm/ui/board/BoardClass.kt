// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.board

import org.mochios.android.ui.components.board.boardClass
import org.mochios.crm.model.CrmDetails
import org.mochios.crm.model.CrmObject
import org.mochios.crm.model.CrmView
import org.mochios.crm.model.FieldOption

/**
 * The objects [view]'s board lays out as cards of their own: those of its
 * classes whose parent it does not show, so they have no card to nest inside.
 */
fun topLevelObjects(objects: List<CrmObject>, view: CrmView): List<CrmObject> {
    val shown = objects.filter { obj -> view.classes.isEmpty() || obj.objectClass in view.classes }
    val ids = shown.mapTo(HashSet(shown.size)) { obj -> obj.id }
    return shown.filter { obj -> obj.parent.isBlank() || obj.parent !in ids }
}

/**
 * The class [view]'s board keeps its columns in, by the rule every client
 * shares. The classes are put in rank order first: the options map beside them
 * arrives keyed in alphabetical order, which is not the CRM's.
 */
fun boardClass(details: CrmDetails, view: CrmView?): String? =
    boardClass(
        classes = details.classes.sortedBy { cls -> cls.rank }.map { cls -> cls.id },
        view = view?.classes.orEmpty(),
    )

/** The options of [field] that [view]'s board shows as its columns or lanes: its board class's. */
fun boardOptions(details: CrmDetails, view: CrmView?, field: String): List<FieldOption> {
    val classId = boardClass(details, view) ?: return emptyList()
    return details.options[classId]?.get(field).orEmpty()
}

/**
 * Whether an object of [classId] takes [value] for [field] when a move or a
 * reparent cascades it from an ancestor, as the server decides: the class has
 * the field and the value is one the field accepts. Fields are per class, so
 * a child of another class may lack the field, or not offer the option, its
 * ancestor's move names. An empty value clears the field and always passes.
 */
fun cascadeAccepts(details: CrmDetails, classId: String, field: String, value: String): Boolean {
    val declared = details.fields[classId]?.firstOrNull { candidate -> candidate.id == field } ?: return false
    if (value.isEmpty()) return true
    val number = { bound: String -> bound.takeIf { text -> NUMBER.matches(text) }?.toDouble() }
    return when (declared.fieldtype) {
        "enumerated" -> details.options[classId]?.get(field).orEmpty().any { option -> option.id == value }
        "checkbox" -> value in setOf("0", "1", "true", "false")
        "number" -> NUMBER.matches(value) && value.toDouble().let { amount ->
            number(declared.min)?.let { minimum -> amount >= minimum } != false &&
                number(declared.max)?.let { maximum -> amount <= maximum } != false
        }
        "date" -> DATE.matches(value) &&
            (declared.min.isEmpty() || value >= declared.min) && (declared.max.isEmpty() || value <= declared.max)
        "text" -> (declared.minlength == 0 || value.length >= declared.minlength) &&
            (declared.maxlength == 0 || value.length <= declared.maxlength)
        "user" -> ENTITY.matches(value)
        else -> true
    }
}

private val NUMBER = Regex("""-?\d+(\.\d+)?""")
private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
private val ENTITY = Regex("""\w{49,51}""")

/**
 * [obj] with those of [values] (field id to value) its class takes
 * ([cascadeAccepts]), or [obj] itself when it takes none.
 */
fun cascaded(details: CrmDetails, obj: CrmObject, values: Map<String, String>): CrmObject {
    var taken: Map<String, Any?>? = null
    for ((field, value) in values) {
        if (obj.stringValue(field) == value) continue
        if (!cascadeAccepts(details, obj.objectClass, field, value)) continue
        taken = (taken ?: obj.values) + (field to value)
    }
    return taken?.let { values -> obj.copy(values = values) } ?: obj
}

/**
 * The board column and lane values a move or a reparent under [parent]
 * copies from it, for every view's column and lane field, as the server
 * copies them: a field the parent holds no value for is cleared.
 */
fun parentValues(details: CrmDetails, parent: CrmObject): Map<String, String> {
    val values = LinkedHashMap<String, String>()
    for (view in details.views) {
        if (view.columns.isNotBlank()) values[view.columns] = parent.stringValue(view.columns)
        if (view.rows.isNotBlank()) values[view.rows] = parent.stringValue(view.rows)
    }
    return values
}
