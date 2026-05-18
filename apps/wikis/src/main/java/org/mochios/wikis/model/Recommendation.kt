package org.mochios.wikis.model

data class Recommendation(
    val id: String = "",
    val name: String = "",
    val blurb: String = "",
    val fingerprint: String = "",
    val server: String = "",
)

data class RecommendationsResponse(
    val wikis: List<Recommendation> = emptyList(),
)
