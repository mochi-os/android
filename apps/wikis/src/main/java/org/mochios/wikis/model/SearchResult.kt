package org.mochios.wikis.model

data class SearchResult(
    val page: String = "",
    val title: String = "",
    val excerpt: String = "",
    val updated: Long = 0,
)

data class SearchResponse(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
)
