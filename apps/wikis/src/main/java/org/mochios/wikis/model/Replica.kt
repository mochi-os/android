package org.mochios.wikis.model

data class Replica(
    val id: String = "",
    val name: String? = null,
    val subscribed: Long = 0,
    val synced: Long = 0,
)
