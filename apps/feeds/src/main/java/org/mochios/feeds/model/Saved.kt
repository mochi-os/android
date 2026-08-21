// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.model

import org.mochios.android.model.Attachment

/**
 * Point-in-time snapshot of a post for Saved. Field names match the web
 * client's snapshot (camelCase `feedId`, `bodyHtml`, ...) so a post saved on
 * one client renders on the other. Only what a read-only card needs is
 * captured.
 */
data class SavedSnapshot(
    val id: String = "",
    val feedId: String = "",
    val feedFingerprint: String = "",
    val feedName: String = "",
    val author: String = "",
    val created: Long = 0,
    val body: String = "",
    val bodyHtml: String = "",
    val data: PostData? = null,
    val attachments: List<Attachment> = emptyList(),
    val tags: List<Tag> = emptyList(),
)

/** One entry from `-/saved/list`: the stored snapshot plus the saved-at time. */
data class SavedItem(
    val post: SavedSnapshot = SavedSnapshot(),
    val created: Long = 0,
)

/** Response body of `-/saved/list`. */
data class SavedListResponse(
    val saved: List<SavedItem> = emptyList(),
    val total: Int = 0,
)

/** Response body of `-/saved/add` / `-/saved/remove` / `-/saved/clear`. */
data class SavedToggleResponse(
    val saved: Boolean = false,
)
