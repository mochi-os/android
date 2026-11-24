// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.components

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import org.mochios.android.ui.components.StatusBadge
import org.mochios.android.ui.components.StatusTone
import org.mochios.android.ui.components.defaultIcon
import org.mochios.staff.R

/**
 * Status chip for the statuses, moderation states, actions and roles the
 * Comptroller stores ([Stored]), mirroring
 * `apps/staff/web/src/components/shared/status-badge.tsx`. A blank status (a
 * draft's moderation) draws nothing; one the table does not know reads
 * "Unknown" and is logged, so a value the server adds later is noticed rather
 * than shown raw. Drawn with the shared [StatusBadge] so a state reads the
 * same in the console as in every other app.
 */
@Composable
fun StaffStatusBadge(status: String, modifier: Modifier = Modifier) {
    val key = status.trim().lowercase()
    if (key.isEmpty()) return
    val label = staffStatusLabelRes(key)
    if (label == null) {
        LaunchedEffect(key) { Log.d("StaffStatusBadge", "No label for status \"$key\"") }
    }
    val tone = staffStatusTone(key)
    StatusBadge(
        label = stringResource(label ?: R.string.staff_status_unknown),
        tone = tone,
        icon = staffStatusIcon(key) ?: tone.defaultIcon,
        modifier = modifier,
    )
}

private fun staffStatusTone(key: String): StatusTone = when (key) {
    "active",
    "published",
    "approved",
    "manual",
    "upheld",
    "resolved_buyer",
    "resolved_seller",
    "actioned",
    "sold",
    "completed",
    "paid",
    "shipped",
    "delivered" -> StatusTone.Positive

    "draft",
    "pending",
    "hold",
    "review",
    "open",
    "responded",
    "appealed" -> StatusTone.Waiting

    "rejected",
    "removed",
    "expired",
    "dismissed",
    "warning",
    "denied",
    "suspended",
    "banned",
    "disputed",
    "cancelled" -> StatusTone.Negative

    "admin" -> StatusTone.Accent

    else -> StatusTone.Neutral
}

private fun staffStatusIcon(key: String): ImageVector? = when (key) {
    "inactive" -> Icons.Outlined.PauseCircle
    "refunded" -> Icons.AutoMirrored.Outlined.Undo
    "disputed" -> Icons.Outlined.ReportProblem
    else -> null
}

/** The label of the stored value [key], or null for one this client does not know. */
@StringRes
internal fun staffStatusLabelRes(key: String): Int? = when (key) {
    // Listing status
    "draft" -> R.string.staff_status_draft
    "active" -> R.string.staff_status_active
    "sold" -> R.string.staff_status_sold
    "expired" -> R.string.staff_status_expired
    "removed" -> R.string.staff_status_removed

    // Listing moderation and the moderation log's actions
    "approved" -> R.string.staff_status_approved
    "review" -> R.string.staff_status_review
    "hold" -> R.string.staff_status_hold
    "manual" -> R.string.staff_status_manual
    "rejected" -> R.string.staff_status_rejected
    "appealed" -> R.string.staff_status_appealed
    "warning" -> R.string.staff_audit_action_listing_warning
    "upheld" -> R.string.staff_appeals_upheld_toast
    "denied" -> R.string.staff_appeals_denied_toast

    // Order
    "paid" -> R.string.staff_status_paid
    "shipped" -> R.string.staff_status_shipped
    "delivered" -> R.string.staff_status_delivered
    "completed" -> R.string.staff_status_completed
    "disputed" -> R.string.staff_status_disputed
    "refunded" -> R.string.staff_status_refunded
    "cancelled" -> R.string.staff_status_cancelled

    // Report
    "pending" -> R.string.staff_status_pending
    "actioned" -> R.string.staff_status_actioned
    "dismissed" -> R.string.staff_status_dismissed

    // Dispute
    "open" -> R.string.staff_status_open
    "responded" -> R.string.staff_status_responded
    "resolved_buyer" -> R.string.staff_status_resolved_buyer
    "resolved_seller" -> R.string.staff_status_resolved_seller

    // Review
    "published" -> R.string.staff_status_published

    // Account
    "suspended" -> R.string.staff_status_suspended
    "banned" -> R.string.staff_status_banned

    // Category
    "inactive" -> R.string.staff_status_inactive

    // Role
    "admin" -> R.string.staff_status_admin
    "moderator" -> R.string.staff_status_moderator
    "support" -> R.string.staff_status_support

    else -> null
}
