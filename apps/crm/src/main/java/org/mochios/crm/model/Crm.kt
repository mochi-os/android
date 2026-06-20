// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.model

data class Crm(
    val id: String = "",
    val fingerprint: String = "",
    val name: String = "",
    val description: String = "",
    val prefix: String = "",
    val counter: Int = 0,
    val owner: Int = 0,
    val ownername: String = "",
    val server: String? = null,
    val created: Long = 0,
    val updated: Long = 0,
    val access: String = ""
)

data class CrmDetails(
    val crm: Crm,
    val classes: List<CrmClass> = emptyList(),
    val fields: Map<String, List<CrmField>> = emptyMap(),
    val options: Map<String, Map<String, List<FieldOption>>> = emptyMap(),
    val views: List<CrmView> = emptyList(),
    val hierarchy: Map<String, List<String>> = emptyMap()
)
