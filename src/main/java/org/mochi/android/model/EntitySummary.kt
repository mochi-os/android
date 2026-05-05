package org.mochi.android.model

data class EntitySummary(
    val id: String,
    val name: String = "",
    val description: String = "",
    val server: String? = null,
    val subscribers: Int = 0,
    val isSubscribed: Boolean = false
)
