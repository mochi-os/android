package org.mochios.wikis.model

data class Redirect(
    val source: String = "",
    val target: String = "",
    val created: Long = 0,
)

data class RedirectsResponse(
    val redirects: List<Redirect> = emptyList(),
)

data class RedirectSetResponse(
    val ok: Boolean = false,
)

data class RedirectDeleteResponse(
    val ok: Boolean = false,
)
