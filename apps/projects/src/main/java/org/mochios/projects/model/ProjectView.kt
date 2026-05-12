package org.mochios.projects.model

data class ProjectView(
    val id: String = "",
    val name: String = "",
    val viewtype: String = "",
    val filter: String = "",
    val columns: String = "",
    val rows: String = "",
    val fields: String = "",
    val sort: String = "",
    val direction: String = "asc",
    val classes: List<String> = emptyList(),
    val rank: Int = 0,
    val border: String = ""
)
