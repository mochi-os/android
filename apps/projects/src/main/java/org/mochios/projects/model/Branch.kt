package org.mochios.projects.model

data class Branch(
    val name: String = "",
    val hash: String = "",
    val isDefault: Boolean = false
)
