// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.util

import org.mochios.projects.model.ProjectClass
import org.mochios.projects.model.ProjectObject

/** The hierarchy entry meaning "may sit at the top level, without a parent". */
const val HIERARCHY_ROOT = ""

/**
 * The `parents` parameter for `hierarchy/set`. The server reads an empty
 * parameter as "may be root", so an empty list - no parent allowed at all -
 * has to be spelt with its sentinel.
 */
fun hierarchyParameter(parents: List<String>): String =
    if (parents.isEmpty()) "_none_" else parents.joinToString(",")

/** Whether an object of [classId] has to be created under a parent. */
fun parentRequired(hierarchy: Map<String, List<String>>, classId: String): Boolean =
    HIERARCHY_ROOT !in hierarchy[classId].orEmpty()

/**
 * The classes a new object can take, given the objects that exist. A class
 * the hierarchy lets sit at the top level always qualifies; one that needs a
 * parent qualifies once an object of an allowed parent class exists. A class
 * with no entries can go nowhere, and the server refuses it.
 */
fun creatableClasses(
    classes: List<ProjectClass>,
    hierarchy: Map<String, List<String>>,
    objects: List<ProjectObject>,
): List<ProjectClass> {
    val present = objects.mapTo(HashSet()) { obj -> obj.objectClass }
    return classes.filter { cls ->
        val parents = hierarchy[cls.id].orEmpty()
        HIERARCHY_ROOT in parents || parents.any { parent -> parent in present }
    }
}

/**
 * The class the create form opens on: one that takes [parentClass] when the
 * form was opened to add a child, else the first of [viewClasses] that can be
 * created, else the first creatable class.
 */
fun initialClass(
    creatable: List<ProjectClass>,
    hierarchy: Map<String, List<String>>,
    parentClass: String?,
    viewClasses: List<String>,
): String {
    val ids = creatable.map { cls -> cls.id }
    val preferred = if (parentClass != null) {
        ids.firstOrNull { id -> parentClass in hierarchy[id].orEmpty() }
    } else {
        viewClasses.firstOrNull { id -> id in ids }
    }
    return preferred ?: ids.firstOrNull().orEmpty()
}

/**
 * The parent a class that needs one starts on: the first of [candidates]
 * carrying every [preset] value - the board column the form was opened from -
 * else the first candidate.
 */
fun defaultParent(
    candidates: List<ProjectObject>,
    preset: Map<String, String>,
): ProjectObject? {
    if (preset.isNotEmpty()) {
        candidates.firstOrNull { obj ->
            preset.all { (field, value) -> obj.stringValue(field) == value }
        }?.let { obj -> return obj }
    }
    return candidates.firstOrNull()
}

/**
 * Whether an object of [childClass] may sit under an object of [parentClass],
 * or at the top level when that is [HIERARCHY_ROOT].
 */
fun parentAllowed(
    hierarchy: Map<String, List<String>>,
    childClass: String,
    parentClass: String,
): Boolean = parentClass in hierarchy[childClass].orEmpty()

/**
 * Whether [child] may be moved under [parentId], or to the top level when it
 * is blank. The server refuses a parent the hierarchy does not allow, and one
 * that is the child itself or below it.
 */
fun reparentAllowed(
    child: ProjectObject,
    parentId: String,
    objects: List<ProjectObject>,
    hierarchy: Map<String, List<String>>,
): Boolean {
    if (parentId.isEmpty()) return parentAllowed(hierarchy, child.objectClass, HIERARCHY_ROOT)
    val parent = objects.firstOrNull { obj -> obj.id == parentId } ?: return false
    return parentAllowed(hierarchy, child.objectClass, parent.objectClass) &&
        parentId !in subtree(child.id, objects)
}

/** The objects [child] may be moved under, in the order given. */
fun parentTargets(
    child: ProjectObject,
    objects: List<ProjectObject>,
    hierarchy: Map<String, List<String>>,
): List<ProjectObject> {
    val excluded = subtree(child.id, objects)
    return objects.filter { obj ->
        obj.id !in excluded && parentAllowed(hierarchy, child.objectClass, obj.objectClass)
    }
}

/** [id] and every object below it. */
private fun subtree(id: String, objects: List<ProjectObject>): Set<String> {
    val byParent = objects.groupBy { obj -> obj.parent }
    val result = mutableSetOf(id)
    val stack = ArrayDeque(listOf(id))
    while (stack.isNotEmpty()) {
        for (obj in byParent[stack.removeLast()].orEmpty()) {
            if (result.add(obj.id)) stack.add(obj.id)
        }
    }
    return result
}
