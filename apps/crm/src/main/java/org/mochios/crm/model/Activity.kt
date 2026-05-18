package org.mochios.crm.model

data class Activity(
    val id: String = "",
    val user: String = "",
    val name: String = "",
    val action: String = "",
    val field: String = "",
    val oldvalue: String = "",
    val newvalue: String = "",
    val created: Long = 0
)
