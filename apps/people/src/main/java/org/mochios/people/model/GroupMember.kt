// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.model

import com.google.gson.annotations.SerializedName

/**
 * [member] is a local user id or a nested group id, per [type]. [name] is empty
 * for a member the server cannot name - a person it cannot look up, a nested
 * group since deleted - and a person carries its [fingerprint] either way.
 */
data class GroupMember(
    val member: String = "",
    val name: String = "",
    val type: GroupMemberType = GroupMemberType.USER,
    val fingerprint: String = "",
)

enum class GroupMemberType {
    @SerializedName("user") USER,
    @SerializedName("group") GROUP,
}
