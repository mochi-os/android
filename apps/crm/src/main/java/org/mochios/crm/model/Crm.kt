// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.model

import com.google.gson.annotations.SerializedName

data class Crm(
    val id: String = "",
    val fingerprint: String = "",
    @SerializedName("fingerprint_hyphens") val fingerprintHyphens: String = "",
    val name: String = "",
    val description: String = "",
    val owner: Owner? = null,
    val server: String? = null,
    val location: String? = null,
    // The peer a `mochi://` share link named; subscribe pins it for the sync.
    val peer: String? = null,
    val created: Long = 0,
    val updated: Long = 0,
    val access: String = ""
)

/**
 * Who a CRM belongs to.
 *
 * @property local Whether the signed-in user owns the CRM on this server.
 * @property name Display name of the owner, empty on remote CRMs that did not
 *   send one.
 */
data class Owner(
    val local: Boolean = false,
    val name: String = ""
)

data class CrmDetails(
    val crm: Crm,
    val classes: List<CrmClass> = emptyList(),
    val fields: Map<String, List<CrmField>> = emptyMap(),
    val options: Map<String, Map<String, List<FieldOption>>> = emptyMap(),
    val views: List<CrmView> = emptyList(),
    val hierarchy: Map<String, List<String>> = emptyMap()
)
