package org.mochios.wikis.model

data class WikiPermissions(
    val view: Boolean = false,
    val edit: Boolean = false,
    val delete: Boolean = false,
    val manage: Boolean = false,
)
