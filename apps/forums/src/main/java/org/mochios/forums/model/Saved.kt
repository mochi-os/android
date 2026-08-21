// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.model

import com.google.gson.annotations.SerializedName
import org.mochios.android.model.Attachment

/**
 * Point-in-time snapshot of a forum post for the Saved (read-later) feature.
 * The schema matches the web client's, so a post saved on one client renders on
 * the other.
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
