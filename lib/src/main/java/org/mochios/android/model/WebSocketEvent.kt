// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

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
    // forums post/reject and comment/reject: the forum owner's
    // machine-readable refusal code, mapped to a message for the author.
    val reason: String? = null,
    // Chat fields
    val event: String? = null,
    val member: String? = null,
    val name: String? = null,
    val body: String? = null,
    val created: Long? = null,
    // UnifiedPush distributor fields. `account` is the accounts.id the
    // distributor acks the queued row with; subId is only the random
    // subscription token. The live envelope spells it sub_id (core writes that
    // one, in accounts.go); the drain response spells it subscription, but that
    // path is read with raw JSON, not here.
    @SerializedName("sub_id") val subId: String? = null,
    val payload: String? = null,
    val account: String? = null,
)
