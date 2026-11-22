// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.util

import org.mochios.projects.model.Link
import org.mochios.projects.model.ProjectClass
import org.mochios.projects.model.ProjectObject

/**
 * What an object is called wherever one is named - a parent choice, a link,
 * a card: its class's title value, else its readable id ([readableId]). The
 * object's own id is never shown. Mirrors entityObjectTitle in lib/web.
 */
fun objectTitle(obj: ProjectObject, classes: List<ProjectClass>, prefix: String): String {
    val field = classes.firstOrNull { cls -> cls.id == obj.objectClass }?.title.orEmpty()
    val title = if (field.isNotBlank()) obj.stringValue(field) else ""
    if (title.isNotBlank()) return title
    return readableId(obj, prefix)
}

/**
 * A linked object this screen does not hold, by what its [link] carries: its
 * title, else the readable id of its number.
 */
fun objectTitle(link: Link, prefix: String): String =
    link.title.ifBlank { readableId(link.number, prefix) }

/**
 * An object's readable id: the server's, else one rebuilt from the project
 * [prefix] and the number. The objects list sends no readable id, so a list
 * fed from it reaches the rebuilt form.
 */
fun readableId(obj: ProjectObject, prefix: String): String =
    obj.readable.ifBlank { readableId(obj.number, prefix) }

/** The readable id of object [number]: "PROJ-12", or "#12" in a project with no prefix. */
fun readableId(number: Int, prefix: String): String =
    if (prefix.isNotBlank()) "$prefix-$number" else "#$number"
