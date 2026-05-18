package org.mochios.wikis.model

data class User(
    val id: String = "",
    val name: String = "",
    val fingerprint: String? = null,
    val relationshipStatus: String? = null,
)

data class UserSearchResponse(
    val results: List<User> = emptyList(),
)
