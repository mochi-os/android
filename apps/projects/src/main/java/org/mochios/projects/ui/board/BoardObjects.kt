// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.board

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
