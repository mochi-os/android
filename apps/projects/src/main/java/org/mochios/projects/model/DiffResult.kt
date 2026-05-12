package org.mochios.projects.model

data class DiffResult(
    val diff: String = "",
    val stats: DiffStats = DiffStats()
)

data class DiffStats(
    val files: Int = 0,
    val additions: Int = 0,
    val deletions: Int = 0
)
