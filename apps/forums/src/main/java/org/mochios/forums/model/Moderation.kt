// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.model

import androidx.annotation.StringRes
import org.mochios.forums.R

/**
 * The moderation screen renders four server enums - a log entry's action, the
 * object a report or log entry is about, and a report's reason. Each maps to a
 * translated label, and an unrecognised value falls back to [humanise] rather
 * than reaching the user as `resolve_report`.
 */

/** The recorded action of a log entry (`log_moderation` in forums.star). */
@StringRes
fun moderationAction(action: String): Int? = when (action) {
    "remove" -> R.string.forums_moderation_action_remove
    "restore" -> R.string.forums_moderation_action_restore
    "approve" -> R.string.forums_moderation_action_approve
    "lock" -> R.string.forums_moderation_action_lock
    "unlock" -> R.string.forums_moderation_action_unlock
    "pin" -> R.string.forums_moderation_action_pin
    "unpin" -> R.string.forums_moderation_action_unpin
    "restrict" -> R.string.forums_moderation_action_restrict
    "unrestrict" -> R.string.forums_moderation_action_unrestrict
    "resolve_report" -> R.string.forums_moderation_action_resolve
    else -> null
}

/** What a report or a log entry is about. */
@StringRes
fun moderationTarget(type: String): Int? = when (type) {
    "post" -> R.string.forums_moderation_target_post
    "comment" -> R.string.forums_moderation_target_comment
    "user" -> R.string.forums_moderation_target_user
    else -> null
}

/** The reason a report was raised with. */
@StringRes
fun moderationReason(reason: String): Int? = when (reason) {
    "spam" -> R.string.forums_moderation_reason_spam
    "harassment" -> R.string.forums_moderation_reason_harassment
    "hate" -> R.string.forums_moderation_reason_hate
    "violence" -> R.string.forums_moderation_reason_violence
    "misinformation" -> R.string.forums_moderation_reason_misinformation
    "offtopic" -> R.string.forums_moderation_reason_offtopic
    "other" -> R.string.forums_moderation_reason_other
    else -> null
}

/**
 * A value with no label of its own, read as a phrase rather than as the raw
 * enum: `resolve_report` becomes `resolve report`. Web does the same.
 */
fun humanise(value: String): String = value.replace('_', ' ')
