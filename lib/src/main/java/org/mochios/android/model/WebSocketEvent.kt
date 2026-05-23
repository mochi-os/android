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
    // Notifications app fields — populated on "read" / "clear_object" events
    // so the Android client can map back to a system-tray tag and cancel.
    val app: String? = null,
    val topic: String? = null,
    val source: String? = null,
    val target: String? = null,
    val sender: String? = null,
    // Chat fields
    val event: String? = null,
    val member: String? = null,
    val name: String? = null,
    val body: String? = null,
    val created: Long? = null,
    // UnifiedPush distributor fields. `account` is the integer accounts.id
    // sent by the server so the distributor can ack the matching push_pending
    // row on live receipt (subId alone is the random subscription token and
    // can't be used to identify the queue row).
    val subId: String? = null,
    val payload: String? = null,
    val account: Long? = null,
)
