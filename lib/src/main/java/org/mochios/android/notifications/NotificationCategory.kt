// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.notifications

import org.mochios.android.util.NaturalCompare

/** Where a category delivers: a push account, a feed or a device. */
data class DestinationRow(
    val type: String = "",
    val target: String = "",
)

/** A notification category, as the notifications and settings apps list them. */
data class NotificationCategory(
    val id: String = "",  // base58 uid (server categories.id is text); "0" = "No notifications"
    /** The stored name. The two seeded categories store English literals. */
    val label: String = "",
    /** The name to show: [label], or its translation while a seeded category is unrenamed. */
    val display: String = "",
    val default: Int = 0,
    val created: Long = 0,
    val destinations: List<DestinationRow> = emptyList(),
) {
    /** What the user reads; a server that predates `display` leaves the label. */
    val shown: String get() = display.ifBlank { label }
}

/**
 * The topic row a notification belongs to, as the notifications app's lookup
 * returns it: keyed by app, topic and object, with the category it is in.
 */
data class NotificationTopic(
    val app: String = "",
    val topic: String = "",
    val `object`: String = "",
    val label: String = "",
    val name: String = "",
    val category: String? = null,
)

/**
 * Categories as every list of them reads: by the name shown, naturally, with
 * the "No notifications" pseudo-category last. The default keeps its place.
 */
fun List<NotificationCategory>.ordered(): List<NotificationCategory> =
    sortedWith(
        compareBy<NotificationCategory> { category -> category.id == "0" }
            .thenBy(NaturalCompare) { category -> category.shown },
    )
