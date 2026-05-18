package org.mochios.wikis.model

data class Group(
    val id: String = "",
    val name: String = "",
)

data class GroupsResponse(
    val groups: List<Group> = emptyList(),
)
