// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.util

import org.mochios.crm.model.CrmClass
import org.mochios.crm.model.CrmObject

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
    classes: List<CrmClass>,
    hierarchy: Map<String, List<String>>,
    objects: List<CrmObject>,
): List<CrmClass> {
    val present = objects.mapTo(HashSet()) { obj -> obj.objectClass }
    return classes.filter { cls ->
        val parents = hierarchy[cls.id].orEmpty()
        HIERARCHY_ROOT in parents || parents.any { parent -> parent in present }
    }
}

/**
 * The parent the create form holds once a class's [candidates] are known:
 * [current] while it is still one of them, else the first candidate for a
 * class that needs a parent, else none - the top level.
 */
fun parentChoice(current: String?, candidates: List<CrmObject>, required: Boolean): String? = when {
    candidates.any { candidate -> candidate.id == current } -> current
    required -> candidates.firstOrNull()?.id
    else -> null
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
    child: CrmObject,
    parentId: String,
    objects: List<CrmObject>,
    hierarchy: Map<String, List<String>>,
): Boolean {
    if (parentId.isEmpty()) return parentAllowed(hierarchy, child.objectClass, HIERARCHY_ROOT)
    val parent = objects.firstOrNull { obj -> obj.id == parentId } ?: return false
    return parentAllowed(hierarchy, child.objectClass, parent.objectClass) &&
        parentId !in subtree(child.id, objects)
}

/** The objects [child] may be moved under, in the order given. */
fun parentTargets(
    child: CrmObject,
    objects: List<CrmObject>,
    hierarchy: Map<String, List<String>>,
): List<CrmObject> {
    val excluded = subtree(child.id, objects)
    return objects.filter { obj ->
        obj.id !in excluded && parentAllowed(hierarchy, child.objectClass, obj.objectClass)
    }
}

/** [id] and every object below it. */
private fun subtree(id: String, objects: List<CrmObject>): Set<String> {
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
