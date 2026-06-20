package org.mochios.people.model

/**
 * A confirmed friend. The `identity` field is the local user's identity id —
 * the people app stores friendships keyed by (identity, friend id) so the
 * same friend can be added from different identities on the same server.
 */
data class Friend(
    val `class`: String = "person",
    val id: String = "",
    val identity: String = "",
    val name: String = "",
    // Friendship creation time (unix seconds), from the server's `created`
    // column. Drives the "Recently added" sort; 0 when absent.
    val created: Long = 0
)
