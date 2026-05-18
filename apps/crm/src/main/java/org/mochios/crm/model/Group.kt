package org.mochios.crm.model

data class Group(
    val id: String = "",
    val name: String = "",
    val members: List<Int> = emptyList()
)
