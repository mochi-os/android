package org.mochios.people.model

/**
 * A user known on this server (the `/-/users/search` endpoint result shape).
 * Distinguished from [User] in that it carries no relationship status or
 * directory metadata — used for picking group members from local accounts.
 */
data class LocalUser(
    val id: String = "",
    val name: String = ""
)
