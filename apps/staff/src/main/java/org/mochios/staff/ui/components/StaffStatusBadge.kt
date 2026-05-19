package org.mochios.staff.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.staff.R

/**
 * Reusable pill-shaped status chip for the staff console.
 *
 * Mirrors `apps/staff/web/src/components/shared/status-badge.tsx`. The web
 * file accepts the union of every status string the staff app surfaces
 * (moderation states, listing/dispute/report/review statuses, account
 * statuses, role badges) and picks a tone for each one — Android does the
 * same here so the listings table, moderation log, account list, reports
 * board, disputes screen, and team management all share one chip.
 *
 * `status` is the raw wire string (`"active"`, `"resolved_buyer"`,
 * `"manual"`, `"admin"`...). Unknown values fall through to a neutral
 * surface chip rendering the raw lowercased string — staff still see the
 * value, just without a colour cue.
 */
@Composable
fun StaffStatusBadge(status: String, modifier: Modifier = Modifier) {
    val key = status.trim().lowercase()
    val tone = staffStatusTone(key)
    val label = staffStatusLabel(key) ?: key

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = tone.foreground,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tone.background)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

private data class StatusTone(val background: Color, val foreground: Color)

@Composable
private fun staffStatusTone(key: String): StatusTone {
    val scheme = MaterialTheme.colorScheme
    return when (key) {
        // Healthy / active / approved-equivalent
        "active",
        "published",
        "auto_approved",
        "soft_approved",
        "approved",
        "manual",
        "resolved_buyer",
        "resolved_seller",
        "reviewed",
        "actioned" ->
            StatusTone(scheme.primaryContainer, scheme.onPrimaryContainer)

        // Completed / sold / admin role
        "sold",
        "completed",
        "admin" ->
            StatusTone(scheme.tertiaryContainer, scheme.onTertiaryContainer)

        // Awaiting attention / pending
        "draft",
        "pending",
        "hold",
        "review",
        "open",
        "responded",
        "reviewing",
        "appealed",
        "moderator",
        "support" ->
            StatusTone(scheme.surfaceVariant, scheme.onSurfaceVariant)

        // Negative / removed / banned
        "rejected",
        "removed",
        "hidden",
        "expired",
        "dismissed",
        "escalated",
        "suspended",
        "banned" ->
            StatusTone(scheme.errorContainer, scheme.onErrorContainer)

        else ->
            StatusTone(scheme.surfaceVariant, scheme.onSurfaceVariant)
    }
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
