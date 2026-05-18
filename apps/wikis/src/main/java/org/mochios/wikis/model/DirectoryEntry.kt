package org.mochios.wikis.model

data class DirectoryEntry(
    val id: String = "",
    val name: String = "",
    val fingerprint: String = "",
    val location: String? = null,
)

data class DirectorySearchResponse(
    val results: List<DirectoryEntry> = emptyList(),
)
