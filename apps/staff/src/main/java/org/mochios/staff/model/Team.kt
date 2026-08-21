// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.model

import com.google.gson.annotations.SerializedName

/**
 * Mirrors `StaffMember` in `apps/staff/web/src/types/team.ts`; `name` and
 * `addedby_name` are resolved server-side and null when the directory cannot
 * resolve the entity.
 */
data class StaffMember(
    val id: String = "",
    val name: String? = null,
    val role: String = "",
    val added: Long = 0,
    val addedby: String = "",
    @SerializedName("addedby_name") val addedbyName: String? = null,
)
