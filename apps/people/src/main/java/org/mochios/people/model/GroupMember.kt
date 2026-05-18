package org.mochios.people.model

import com.google.gson.annotations.SerializedName

/**
 * One row in a group's member list. The `member` field is the id of the
 * referenced subject (numeric local user id or nested group id); [type]
 * distinguishes which kind.
 */
data class GroupMember(
    val member: String = "",
    val name: String = "",
    val type: GroupMemberType = GroupMemberType.USER
)

enum class GroupMemberType {
    @SerializedName("user") USER,
    @SerializedName("group") GROUP,
}
