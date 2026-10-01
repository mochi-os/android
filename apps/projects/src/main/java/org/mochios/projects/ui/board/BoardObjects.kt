// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.board

import org.mochios.android.ui.components.board.boardClass
import org.mochios.projects.model.FieldOption
import org.mochios.projects.model.ProjectDetails
import org.mochios.projects.model.ProjectObject
import org.mochios.projects.model.ProjectView

/**
 * The objects a board lays out: those with no parent among [objects] (a parent
 * outside the set counts as none), narrowed to the view's classes when it has
 * any. One pass over the ids, so a large board does not rescan the list per
 * object.
 */
fun topLevelObjects(objects: List<ProjectObject>, view: ProjectView): List<ProjectObject> {
    val ids = objects.mapTo(HashSet(objects.size)) { obj -> obj.id }
    return objects.filter { obj ->
        (obj.parent.isBlank() || obj.parent !in ids) &&
            (view.classes.isEmpty() || obj.objectClass in view.classes)
    }
}

/**
 * The class [view]'s board keeps its columns in, by the rule every client
 * shares. The classes are put in rank order first: the options map beside them
 * arrives keyed in alphabetical order, which is not the project's.
 */
fun boardClass(details: ProjectDetails, view: ProjectView?): String? =
    boardClass(
        classes = details.classes.sortedBy { cls -> cls.rank }.map { cls -> cls.id },
        view = view?.classes.orEmpty(),
    )

/** The options of [field] that [view]'s board shows as its columns or lanes: its board class's. */
fun boardOptions(details: ProjectDetails, view: ProjectView?, field: String): List<FieldOption> {
    val classId = boardClass(details, view) ?: return emptyList()
    return details.options[classId]?.get(field).orEmpty()
}

/**
 * Every option [field] can take across the project's classes, in class order,
 * one per id: what a filter offers, and where a value is looked up when its
 * own class defines no options for it.
 */
fun allOptions(details: ProjectDetails, field: String): List<FieldOption> {
    val seen = mutableSetOf<String>()
    return details.classes.sortedBy { cls -> cls.rank }.flatMap { cls ->
        details.options[cls.id]?.get(field).orEmpty().filter { option -> seen.add(option.id) }
    }
}
