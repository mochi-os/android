// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.model

import com.google.gson.annotations.SerializedName

/**
 * Mirrors `Thread` in web `types/threads.ts`. `order` is 0 for pre-purchase
 * enquiries; the nullable fields are denormalised onto list rows.
 */
data class MarketThread(
    val id: String = "",
    val listing: String = "",
    val order: String = "",
    val buyer: String = "",
    val seller: String = "",
    val created: Long = 0,
    val updated: Long = 0,
    val title: String? = null,
    @SerializedName("last_message") val lastMessage: String? = null,
    @SerializedName("last_message_time") val lastMessageTime: Long? = null,
    val unread: Long? = null,
    @SerializedName("other_name") val otherName: String? = null,
)

/**
 * Mirrors `Message` in web `types/threads.ts`; `read` is 0 or the timestamp it
 * was read.
 */
data class Message(
    val id: String = "",
    val thread: String = "",
    val sender: String = "",
    @SerializedName("sender_name") val senderName: String = "",
    val body: String = "",
    val read: Long = 0,
    val created: Long = 0,
)
