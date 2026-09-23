// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.util

import org.mochios.projects.model.ProjectClass
import org.mochios.projects.model.ProjectObject

/**
 * What an object is called wherever one is named - a parent choice, a link,
 * a card: its class's title value, else the server's readable id, else one
 * rebuilt from the project [prefix] and the number. The objects list sends
 * no readable id, so a picker fed from it reaches the rebuilt form; the
 * object's own id is never shown. Mirrors entityObjectTitle in lib/web.
 */
fun objectTitle(obj: ProjectObject, classes: List<ProjectClass>, prefix: String): String {
    val field = classes.firstOrNull { cls -> cls.id == obj.objectClass }?.title.orEmpty()
    val title = if (field.isNotBlank()) obj.stringValue(field) else ""
    if (title.isNotBlank()) return title
    if (obj.readable.isNotBlank()) return obj.readable
    return if (prefix.isNotBlank()) "$prefix-${obj.number}" else "#${obj.number}"
}
