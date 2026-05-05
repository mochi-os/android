package org.mochi.android.model

import com.google.gson.annotations.SerializedName

data class WebSocketEvent(
    val type: String,
    val feed: String? = null,
    val project: String? = null,
    val post: String? = null,
    val comment: String? = null,
    val id: String? = null,
    @SerializedName("object") val objectId: String? = null,
    val source: String? = null,
    val target: String? = null,
    val sender: String? = null
)
