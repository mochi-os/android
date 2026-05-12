package org.mochios.projects.model

data class Project(
    val id: String = "",
    val fingerprint: String = "",
    val name: String = "",
    val description: String = "",
    val prefix: String = "",
    val counter: Int = 0,
    val owner: Int = 0,
    val ownername: String = "",
    val server: String? = null,
    val created: Long = 0,
    val updated: Long = 0,
    val access: String = ""
)

data class ProjectDetails(
    val project: Project,
    val classes: List<ProjectClass> = emptyList(),
    val fields: Map<String, List<ProjectField>> = emptyMap(),
    val options: Map<String, Map<String, List<FieldOption>>> = emptyMap(),
    val views: List<ProjectView> = emptyList(),
    val hierarchy: Map<String, List<String>> = emptyMap()
)
