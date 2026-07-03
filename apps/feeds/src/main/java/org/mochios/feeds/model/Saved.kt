// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.model

import org.mochios.android.model.Attachment

/**
 * A point-in-time snapshot of a post, stored server-side for the "Saved"
 * (read-later) feature. The schema deliberately matches the web client's
 * snapshot (camelCase `feedId`/`feedFingerprint`/`feedName`/`bodyHtml`) so a
 * post saved on one client renders on the other — saved data syncs across the
 * user's devices via the feeds app's per-user replication.
 *
 * Only the fields needed to render a read-only card are captured; the card
 * links back to the live post for everything else. [data] carries the RSS
 * title/image (and check-in/travelling) exactly as on a live [Post]; reaction
 * counts are ignored and defaulted when absent — graceful degradation either way.
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
