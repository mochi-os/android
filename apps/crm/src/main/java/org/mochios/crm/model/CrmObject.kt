// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.model

import com.google.gson.annotations.SerializedName

data class CrmObject(
    val id: String = "",
    val crm: String = "",
    @SerializedName("class") val objectClass: String = "",
    val number: Int = 0,
    val parent: String = "",
    val rank: Int = 0,
    val created: Long = 0,
    val updated: Long = 0,
    val readable: String = "",
    val values: Map<String, Any?> = emptyMap()
) {
    fun stringValue(fieldId: String): String = values[fieldId]?.toString() ?: ""

    fun listValue(fieldId: String): List<String> =
        (values[fieldId] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
}
