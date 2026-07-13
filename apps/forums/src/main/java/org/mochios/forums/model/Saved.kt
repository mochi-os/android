// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.model

import com.google.gson.annotations.SerializedName
import org.mochios.android.model.Attachment

/**
 * A point-in-time snapshot of a forum post, stored server-side for the "Saved"
 * (read-later) feature. The schema matches the web client's snapshot so a post
 * saved on one client renders on the other — saved data syncs across the user's
 * devices via the forums app's per-user replication. Only the fields needed for
 * a read-only card are captured; the card links back to the live thread.
 */
data class SavedSnapshot(
    val id: String = "",
    val forum: String = "",
    val fingerprint: String = "",
    val forumName: String = "",
    val member: String = "",
    val name: String = "",
    val title: String = "",
    val body: String = "",
    @SerializedName("body_markdown") val bodyMarkdown: String = "",
    val created: Long = 0,
    val up: Int = 0,
    val down: Int = 0,
    val tags: List<Tag> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
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
