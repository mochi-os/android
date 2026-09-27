// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import org.mochios.android.ui.components.StatusBadge
import org.mochios.android.ui.components.StatusTone
import org.mochios.android.ui.components.defaultIcon
import org.mochios.staff.R

/**
 * Status chip for every wire status string the console shows, mirroring
 * `apps/staff/web/src/components/shared/status-badge.tsx`; unknown values
 * render neutrally with the raw string. Drawn with the shared [StatusBadge] so
 * a state reads the same in the console as in every other app.
 */
@Composable
fun StaffStatusBadge(status: String, modifier: Modifier = Modifier) {
    val key = status.trim().lowercase()
    val tone = staffStatusTone(key)
    StatusBadge(
        label = staffStatusLabel(key) ?: key,
        tone = tone,
        icon = staffStatusIcon(key) ?: tone.defaultIcon,
        modifier = modifier,
    )
}

private fun staffStatusTone(key: String): StatusTone = when (key) {
    "active",
    "published",
    "auto_approved",
    "soft_approved",
    "approved",
    "manual",
    "resolved_buyer",
    "resolved_seller",
    "reviewed",
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
    "reviewing",
    "appealed",
    "paused" -> StatusTone.Waiting

    "rejected",
    "removed",
    "hidden",
    "expired",
    "dismissed",
    "escalated",
    "suspended",
    "banned",
    "disputed",
    "cancelled",
    "past_due" -> StatusTone.Negative

    "admin" -> StatusTone.Accent

    else -> StatusTone.Neutral
}

private fun staffStatusIcon(key: String): ImageVector? = when (key) {
    "inactive" -> Icons.Outlined.PauseCircle
    "refunded" -> Icons.AutoMirrored.Outlined.Undo
    "disputed" -> Icons.Outlined.ReportProblem
    "past_due" -> Icons.Outlined.EventBusy
    else -> null
}

@Composable
private fun staffStatusLabel(key: String): String? = when (key) {
    // Listing
    "draft" -> stringResource(R.string.staff_status_draft)
    "active" -> stringResource(R.string.staff_status_active)
    "sold" -> stringResource(R.string.staff_status_sold)
    "expired" -> stringResource(R.string.staff_status_expired)
    "removed" -> stringResource(R.string.staff_status_removed)
    "rejected" -> stringResource(R.string.staff_status_rejected)

    // Moderation
    "pending" -> stringResource(R.string.staff_status_pending)
    "hold" -> stringResource(R.string.staff_status_hold)
    "review" -> stringResource(R.string.staff_status_review)
    "auto_approved" -> stringResource(R.string.staff_status_auto_approved)
    "soft_approved" -> stringResource(R.string.staff_status_soft_approved)
    "manual" -> stringResource(R.string.staff_status_manual)
    "approved" -> stringResource(R.string.staff_status_approved)
    "appealed" -> stringResource(R.string.staff_status_appealed)

    // Dispute
    "open" -> stringResource(R.string.staff_status_open)
    "responded" -> stringResource(R.string.staff_status_responded)
    "reviewing" -> stringResource(R.string.staff_status_reviewing)
    "resolved_buyer" -> stringResource(R.string.staff_status_resolved_buyer)
    "resolved_seller" -> stringResource(R.string.staff_status_resolved_seller)
    "escalated" -> stringResource(R.string.staff_status_escalated)

    // Report
    "reviewed" -> stringResource(R.string.staff_status_reviewed)
    "actioned" -> stringResource(R.string.staff_status_actioned)
    "dismissed" -> stringResource(R.string.staff_status_dismissed)

    // Review
    "published" -> stringResource(R.string.staff_status_published)
    "hidden" -> stringResource(R.string.staff_status_hidden)

    // Account
    "suspended" -> stringResource(R.string.staff_status_suspended)
    "banned" -> stringResource(R.string.staff_status_banned)

    // Role
    "admin" -> stringResource(R.string.staff_status_admin)
    "moderator" -> stringResource(R.string.staff_status_moderator)
    "support" -> stringResource(R.string.staff_status_support)

    else -> null
}
