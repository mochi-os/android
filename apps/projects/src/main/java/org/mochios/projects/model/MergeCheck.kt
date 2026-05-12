package org.mochios.projects.model

data class MergeCheck(
    val canMerge: Boolean = false,
    val conflicts: List<String> = emptyList(),
    val base: String = "",
    val ahead: Int = 0,
    val behind: Int = 0
)
