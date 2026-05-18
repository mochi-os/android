package org.mochios.wikis.model

data class Change(
    val id: String = "",
    val slug: String = "",
    val title: String = "",
    val author: String = "",
    val name: String = "",
    val created: Long = 0,
    val version: Int = 0,
    val comment: String = "",
)

data class ChangesResponse(
    val changes: List<Change> = emptyList(),
)
