// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf

/**
 * [selected] as the web's Reply quotes the selection: trimmed, each line
 * marked `> `, and a blank line after; "" when nothing is selected.
 */
fun quote(selected: String?): String {
    val text = selected?.trim().orEmpty()
    if (text.isEmpty()) return ""
    return text.split("\n").joinToString("\n") { line -> "> $line" } + "\n\n"
}

/**
 * [draft] as a Reply leaves it: the [quote] of [selected] at its head, in
 * place of [replacing], the quote the last Reply put there, while the draft
 * still starts with it. The web's Reply sets its reply box to the quote alone,
 * so a second Reply never stacks a second quote, and one with nothing
 * selected leaves none; this composer also holds what the user typed and what
 * a Quote cited, which stay.
 */
fun quoteInto(draft: String, selected: String?, replacing: String = ""): String {
    val rest = if (replacing.isNotEmpty() && draft.startsWith(replacing)) draft.substring(replacing.length) else draft
    return quote(selected) + rest
}

/**
 * An unsent comment: its text, the comment it answers, the files picked for
 * it, and the quote the last Reply put at the head of its text.
 */
data class CommentDraft(
    val text: String = "",
    val reply: String? = null,
    val files: List<Uri> = emptyList(),
    val quote: String = "",
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

    /**
     * Readies the unsent comment on [target] as a reply to [comment], quoting
     * [selected] as [quoteInto] does.
     */
    fun reply(target: String, comment: String, selected: String?) {
        update(target) { draft ->
            draft.copy(reply = comment, text = quoteInto(draft.text, selected, draft.quote), quote = quote(selected))
        }
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
