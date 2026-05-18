package org.mochios.wikis.model

data class WikiInfo(
    val id: String = "",
    val name: String = "",
    val home: String = "",
    val fingerprint: String? = null,
    val source: String? = null,
)

data class WikiInfoResponse(
    val entity: Boolean = false,
    val wiki: WikiInfo? = null,
    val wikis: List<WikiInfo>? = null,
    val permissions: WikiPermissions? = null,
    val fingerprint: String? = null,
)
