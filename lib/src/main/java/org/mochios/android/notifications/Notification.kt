// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.notifications

data class MochiNotification(
    val id: String = "",
    val app: String = "",
    val topic: String = "",
    val `object`: String = "",
    val title: String = "",
    val content: String = "",
    val link: String = "",
    val sender: String = "",
    val count: Int = 1,
    val created: Long = 0,
    val read: Long = 0,
) {
    val isUnread: Boolean get() = read == 0L
}

data class NotificationsListResponse(
    val data: List<MochiNotification> = emptyList(),
    val count: Int = 0,
    val total: Int = 0,
    val rss: Boolean = false,
)

data class NotificationsCount(
    val count: Int = 0,
    val total: Int = 0,
)

/**
 * One of the user's RSS feeds of their notifications, read at its address by
 * [token]. [enabled] is 1 when the feed joins each new category, the web's
 * "Notify by default".
 */
data class RssFeed(
    val id: String = "",
    val name: String = "",
    val token: String = "",
    val created: Long = 0,
    val enabled: Int = 0,
)
