package org.mochios.android.model

import com.google.gson.annotations.SerializedName

data class WebSocketEvent(
    val type: String? = null,
    val feed: String? = null,
    val project: String? = null,
    val post: String? = null,
    val comment: String? = null,
    val id: String? = null,
    @SerializedName("object") val objectId: String? = null,
    val source: String? = null,
    val target: String? = null,
    val sender: String? = null,
    // Chat fields
    val event: String? = null,
    val member: String? = null,
    val name: String? = null,
    val body: String? = null,
    val created: Long? = null,
    // UnifiedPush distributor fields
    val subId: String? = null,
    val payload: String? = null,
)
