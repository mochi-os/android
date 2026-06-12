package org.mochios.forums.model

/**
 * A person returned by the forums backend's `users/search` proxy (which calls
 * people.users/search). `id` is a full entity id — used directly as the access
 * subject, matching web's `selectedUser.id`.
 */
data class User(
    val id: String = "",
    val name: String = "",
)

/**
 * A friend-group returned by the forums backend's `groups` proxy (people
 * groups/list). Used as an access subject prefixed with `@`, matching web.
 */
data class Group(
    val id: String = "",
    val name: String = "",
)
