// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.model

import com.google.gson.annotations.SerializedName

/**
 * `event_staff_me` response: `role` is "" for an authenticated non-staff
 * caller, and `id` is also "" with no identity. A blank role means not staff.
 */
data class Me(
    val id: String = "",
    val role: String = "",
)

/**
 * Mirrors `VALID_STAFF_ROLES` in `apps/comptroller/starlark/comptroller.star`;
 * an unknown role is treated as non-staff.
 */
enum class Role {
    @SerializedName("admin") ADMIN,
    @SerializedName("moderator") MODERATOR,
    @SerializedName("support") SUPPORT,
}
