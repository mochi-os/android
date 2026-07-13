// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.model

import com.google.gson.annotations.SerializedName

data class ProjectObject(
    val id: String = "",
    val project: String = "",
    @SerializedName("class") val objectClass: String = "",
    val number: Int = 0,
    val parent: String = "",
    // Fractional-index text key (#53): an opaque, ASCII-sortable ordering key
    // (e.g. "a0"), not a dense integer — compare/sort it as a string. Moves send
    // an integer drop position (server computes the new key); see MoveObjectSheet.
    val rank: String = "",
    val created: Long = 0,
    val updated: Long = 0,
    val readable: String = "",
    val values: Map<String, Any?> = emptyMap()
) {
    fun stringValue(fieldId: String): String = values[fieldId]?.toString() ?: ""

    fun listValue(fieldId: String): List<String> =
        (values[fieldId] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
}
