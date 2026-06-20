// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.model

import com.google.gson.annotations.SerializedName

/**
 * A buyer/seller conversation, optionally pinned to a specific listing/order.
 *
 * Mirrors `Thread` in `apps/market/web/src/types/threads.ts`. `order` is 0
 * for pre-purchase enquiry threads. `last_message` / `unread` /
 * `other_name` are denormalised onto thread-list rows so the UI can render
 * an inbox without an extra fetch per row.
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
 * A single message inside a [MarketThread].
 *
 * Mirrors `Message` in `apps/market/web/src/types/threads.ts`. `read` is 0
 * (unread) or the timestamp the recipient marked it read.
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
