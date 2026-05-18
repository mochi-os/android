package org.mochios.wikis.model

data class AccessRule(
    val id: Int? = null,
    val subject: String = "",
    val operation: String = "",
    val grant: Int = 0,
    val name: String? = null,
    val isOwner: Boolean? = null,
)

data class AccessListResponse(
    val rules: List<AccessRule> = emptyList(),
)
