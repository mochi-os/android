// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.model

import com.google.gson.annotations.SerializedName

/**
 * One row in the staff team list (`team/list`).
 *
 * Mirrors `StaffMember` in `apps/staff/web/src/types/team.ts`. `name` and
 * `addedby_name` are resolved server-side in `action_team_list` via
 * `mochi.entity.name(id)` and are nullable when the directory can't resolve
 * the entity. `role` is one of the lowercase values in [Role].
 */
data class StaffMember(
    val id: String = "",
    val name: String? = null,
    val role: String = "",
    val added: Long = 0,
    val addedby: String = "",
    @SerializedName("addedby_name") val addedbyName: String? = null,
)
