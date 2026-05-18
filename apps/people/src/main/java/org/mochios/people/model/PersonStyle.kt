package org.mochios.people.model

/**
 * Optional per-person presentation tweaks. Only the accent colour is
 * currently used; the type is open-ended so additional style hints can
 * be added without breaking existing clients.
 */
data class PersonStyle(
    val accent: String? = null
)
