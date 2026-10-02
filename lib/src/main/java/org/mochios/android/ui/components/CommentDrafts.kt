// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf

/** An unsent comment: its text, the comment it answers, and the files picked for it. */
data class CommentDraft(
    val text: String = "",
    val reply: String? = null,
    val files: List<Uri> = emptyList(),
)

/**
 * Comment text not yet sent, for a holder that outlives the screen it was
 * typed on, such as a view model: one new comment per target and one edit per
 * comment. State remembered by the composable is gone as soon as the tab or
 * sheet showing it leaves, which a stray swipe is enough to do.
 */
class CommentDrafts {
    private val drafts = mutableStateMapOf<String, CommentDraft>()
    private val edits = mutableStateMapOf<String, String>()

    /** The unsent comment on [target], empty when there is none. */
    fun draft(target: String): CommentDraft = drafts[target] ?: CommentDraft()

    /** Applies [change] to the unsent comment on [target]. */
    fun update(target: String, change: (CommentDraft) -> CommentDraft) {
        drafts[target] = change(draft(target))
    }

    /** Drops the unsent comment on [target], once it is sent. */
    fun clear(target: String) {
        drafts.remove(target)
    }

    /** The text of the edit under way on [comment], or null when it is not being edited. */
    fun edit(comment: String): String? = edits[comment]

    /** Keeps [text] as the edit under way on [comment]; null ends the edit. */
    fun edit(comment: String, text: String?) {
        if (text == null) edits.remove(comment) else edits[comment] = text
    }
}
