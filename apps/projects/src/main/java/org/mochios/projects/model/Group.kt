package org.mochios.projects.model

data class Group(
    val id: String = "",
    val name: String = "",
    val members: List<Int> = emptyList()
)
