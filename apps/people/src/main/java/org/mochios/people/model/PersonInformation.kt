package org.mochios.people.model

/**
 * Public profile information for a person entity. The endpoint is location-
 * transparent — local entities are served from this server, remote ones go
 * out over P2P via `mochi.remote.stream`. The image slots (`avatar`,
 * `banner`, `favicon`) contain attachment ids; the client constructs URLs
 * to the streaming endpoints itself.
 */
data class PersonInformation(
    val id: String = "",
    val fingerprint: String = "",
    val name: String = "",
    val privacy: String = "",
    val profile: String = "",
    val style: PersonStyle = PersonStyle(),
    val avatar: String = "",
    val banner: String = "",
    val favicon: String = ""
)
