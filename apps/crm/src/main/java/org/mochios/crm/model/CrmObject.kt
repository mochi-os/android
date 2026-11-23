// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.model

import com.google.gson.annotations.SerializedName

data class CrmObject(
    val id: String = "",
    val crm: String = "",
    @SerializedName("class") val objectClass: String = "",
    val parent: String = "",
    // Fractional-index text key: an opaque, ASCII-sortable ordering key (e.g.
    // "a0"), not a dense integer, so compare and sort it as a string. A move
    // names the object it goes after or before and the server mints the new
    // key; see CrmViewModel.moveObject.
    val rank: String = "",
    val created: Long = 0,
    val updated: Long = 0,
    val values: Map<String, Any?> = emptyMap()
) {
    // Every value arrives as a string; a field holds one.
    fun stringValue(fieldId: String): String = values[fieldId]?.toString() ?: ""
}
