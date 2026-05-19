package org.mochios.staff.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.staff.R
import org.mochios.staff.model.AuditEntry
import org.mochios.staff.repository.StaffRepository
import javax.inject.Inject

/**
 * Reusable per-object audit timeline.
 *
 * Renders a vertical dot-and-line history of the rows the Comptroller has
 * recorded against a given `(kind, objectId)` tuple. Mirrors the web
 * [audit-timeline](apps/staff/web/src/components/shared/audit-timeline.tsx)
 * component used by the listings / accounts / disputes / reports dialogs.
 *
 *   - `kind`     — audit-timeline kind ("listing", "order", "dispute",
 *                  "account", "review", "report", ...).
 *   - `objectId` — the underlying row id. Numeric ids are passed as decimal
 *                  strings; account ids as fingerprints.
 *
 * Empty state ("No audit history") is rendered inline. Error state is
 * surfaced as a small inline message — the timeline is decorative within
 * a host dialog, so we don't escalate to a fullscreen error.
 *
 * The composable owns a small Hilt-assisted ViewModel so callers don't
 * have to thread the repository through their own ViewModel. Embed it
 * anywhere — moderation dialogs, future detail drawers, etc.
 */
@Composable
fun StaffAuditTimeline(
    kind: String,
    objectId: String,
    modifier: Modifier = Modifier,
) {
    // Key on (kind, objectId) so each call site gets its own ViewModel
    // instance — otherwise nested dialogs hosting two different timelines
    // would share state via the parent NavBackStackEntry-scoped Hilt VM.
    key(kind, objectId) {
        val viewModel: AuditTimelineHostViewModel = hiltViewModel(key = "audit:$kind:$objectId")
        LaunchedEffect(kind, objectId) {
            viewModel.load(kind, objectId)
        }
        val state by viewModel.state.collectAsState()
        AuditTimelineBody(state = state, modifier = modifier)
    }
}

@Composable
private fun AuditTimelineBody(
    state: AuditTimelineUiState,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }

        state.error != null -> Text(
            text = state.error.userMessage(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier.padding(vertical = 4.dp),
        )

        state.entries.isEmpty() -> Text(
            text = stringResource(R.string.staff_audit_empty),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = 8.dp),
        )

        else -> Column(modifier = modifier) {
            Text(
                text = stringResource(R.string.staff_audit_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            state.entries.forEachIndexed { index, entry ->
                AuditRow(entry = entry, isLast = index == state.entries.lastIndex)
            }
        }
    }
}

@Composable
private fun AuditRow(entry: AuditEntry, isLast: Boolean) {
    val format = LocalFormat.current
    val label = actionLabel(entry.action)
    val detail = auditDetail(entry)
    val actor = when {
        entry.actor == "system" || entry.actor.isBlank() ->
            stringResource(R.string.staff_audit_actor_system)
        entry.actorName.isNotBlank() -> entry.actorName
        else -> shortFingerprint(entry.actor)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (isLast) 0.dp else 10.dp),
    ) {
        // Dot + connector line gutter.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            if (!isLast) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                )
                if (detail.isNotBlank()) {
                    Text(
                        text = ": $detail",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "${format.formatTimestamp(entry.timestamp)} · $actor",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun actionLabel(action: String): String {
    // Mirrors web's `useActionLabels()` — every action the Comptroller can
    // emit is mapped to a localised label here. Unknown actions fall through
    // to a capitalised version of the raw key so the timeline never shows
    // bare snake_case to staff.
    return when (action) {
        // Listing actions
        "listing.created" -> stringResource(R.string.staff_audit_action_listing_created)
        "listing.updated" -> stringResource(R.string.staff_audit_action_listing_updated)
        "listing.deleted" -> stringResource(R.string.staff_audit_action_listing_deleted)
        "listing.published" -> stringResource(R.string.staff_audit_action_listing_published)
        "listing.relisted" -> stringResource(R.string.staff_audit_action_listing_relisted)
        "listing.expired" -> stringResource(R.string.staff_audit_action_listing_expired)
        "listing.removed" -> stringResource(R.string.staff_audit_action_listing_removed)
        "listing.approved" -> stringResource(R.string.staff_audit_action_listing_approved)
        "listing.rejected" -> stringResource(R.string.staff_audit_action_listing_rejected)
        "listing.warning" -> stringResource(R.string.staff_audit_action_listing_warning)
        "listing.appeal_submitted" -> stringResource(R.string.staff_audit_action_listing_appeal_submitted)
        "listing.appeal_decided" -> stringResource(R.string.staff_audit_action_listing_appeal_decided)

        // Account actions
        "account.seller_activated" -> stringResource(R.string.staff_audit_action_account_seller_activated)
        "account.stripe_connected" -> stringResource(R.string.staff_audit_action_account_stripe_connected)
        "account.stripe_onboarded" -> stringResource(R.string.staff_audit_action_account_stripe_onboarded)
        "account.stripe_restricted" -> stringResource(R.string.staff_audit_action_account_stripe_restricted)
        "account.stripe_disconnected" -> stringResource(R.string.staff_audit_action_account_stripe_disconnected)
        "account.suspended" -> stringResource(R.string.staff_audit_action_account_suspended)
        "account.unsuspended" -> stringResource(R.string.staff_audit_action_account_unsuspended)
        "account.banned" -> stringResource(R.string.staff_audit_action_account_banned)
        "account.unbanned" -> stringResource(R.string.staff_audit_action_account_unbanned)

        // Order
        "order.created" -> stringResource(R.string.staff_audit_action_order_created)
        "order.paid" -> stringResource(R.string.staff_audit_action_order_paid)
        "order.payment_failed" -> stringResource(R.string.staff_audit_action_order_payment_failed)
        "order.shipped" -> stringResource(R.string.staff_audit_action_order_shipped)
        "order.completed" -> stringResource(R.string.staff_audit_action_order_completed)
        "order.refunded" -> stringResource(R.string.staff_audit_action_order_refunded)
        "order.cancelled" -> stringResource(R.string.staff_audit_action_order_cancelled)
        "order.chargeback" -> stringResource(R.string.staff_audit_action_order_chargeback)
        "order.chargeback_won" -> stringResource(R.string.staff_audit_action_order_chargeback_won)
        "order.chargeback_lost" -> stringResource(R.string.staff_audit_action_order_chargeback_lost)

        // Dispute
        "dispute.opened" -> stringResource(R.string.staff_audit_action_dispute_opened)
        "dispute.responded" -> stringResource(R.string.staff_audit_action_dispute_responded)
        "dispute.resolved_buyer" -> stringResource(R.string.staff_audit_action_dispute_resolved_buyer)
        "dispute.resolved_seller" -> stringResource(R.string.staff_audit_action_dispute_resolved_seller)

        // Auction
        "auction.created" -> stringResource(R.string.staff_audit_action_auction_created)
        "auction.opened" -> stringResource(R.string.staff_audit_action_auction_opened)
        "auction.bid_placed" -> stringResource(R.string.staff_audit_action_auction_bid_placed)
        "auction.ended_sold" -> stringResource(R.string.staff_audit_action_auction_ended_sold)
        "auction.ended_unsold" -> stringResource(R.string.staff_audit_action_auction_ended_unsold)
        "auction.cancelled" -> stringResource(R.string.staff_audit_action_auction_cancelled)
        "auction.payment_overdue" -> stringResource(R.string.staff_audit_action_auction_payment_overdue)

        // Subscription
        "subscription.created" -> stringResource(R.string.staff_audit_action_subscription_created)
        "subscription.activated" -> stringResource(R.string.staff_audit_action_subscription_activated)
        "subscription.active" -> stringResource(R.string.staff_audit_action_subscription_active)
        "subscription.cancel_scheduled" -> stringResource(R.string.staff_audit_action_subscription_cancel_scheduled)
        "subscription.cancelled" -> stringResource(R.string.staff_audit_action_subscription_cancelled)
        "subscription.paused" -> stringResource(R.string.staff_audit_action_subscription_paused)
        "subscription.resumed" -> stringResource(R.string.staff_audit_action_subscription_resumed)
        "subscription.reactivated" -> stringResource(R.string.staff_audit_action_subscription_reactivated)
        "subscription.past_due" -> stringResource(R.string.staff_audit_action_subscription_past_due)
        "subscription.chargeback" -> stringResource(R.string.staff_audit_action_subscription_chargeback)
        "subscription.chargeback_won" -> stringResource(R.string.staff_audit_action_subscription_chargeback_won)
        "subscription.chargeback_lost" -> stringResource(R.string.staff_audit_action_subscription_chargeback_lost)

        // Reports / reviews
        "report.created" -> stringResource(R.string.staff_audit_action_report_created)
        "report.actioned" -> stringResource(R.string.staff_audit_action_report_actioned)
        "review.created" -> stringResource(R.string.staff_audit_action_review_created)
        "review.responded" -> stringResource(R.string.staff_audit_action_review_responded)
        "review.revealed" -> stringResource(R.string.staff_audit_action_review_revealed)
        "review.hide" -> stringResource(R.string.staff_audit_action_review_hide)
        "review.remove" -> stringResource(R.string.staff_audit_action_review_remove)
        "review.restore" -> stringResource(R.string.staff_audit_action_review_restore)

        // Category
        "category.created" -> stringResource(R.string.staff_audit_action_category_created)
        "category.updated" -> stringResource(R.string.staff_audit_action_category_updated)
        "category.deleted" -> stringResource(R.string.staff_audit_action_category_deleted)

        // Staff / config
        "staff.team_added" -> stringResource(R.string.staff_audit_action_staff_team_added)
        "staff.team_removed" -> stringResource(R.string.staff_audit_action_staff_team_removed)
        "staff.team_role_changed" -> stringResource(R.string.staff_audit_action_staff_team_role_changed)
        "config.updated" -> stringResource(R.string.staff_audit_action_config_updated)
        "config.thresholds_updated" -> stringResource(R.string.staff_audit_action_config_thresholds_updated)

        else -> action.substringAfter('.', action).replace('_', ' ')
            .replaceFirstChar { it.uppercase() }
    }
}

/**
 * Extract a short human-readable detail from the audit entry's JSON `data`
 * blob. Keep this best-effort — the wire shape is open and varies per
 * action. Returns an empty string when no useful fields are present.
 */
private fun auditDetail(entry: AuditEntry): String {
    if (entry.data.isBlank()) return ""
    val payload = try {
        JSONObject(entry.data)
    } catch (_: Exception) {
        return ""
    }
    val parts = mutableListOf<String>()
    if (payload.has("reason")) {
        val reason = payload.optString("reason")
        if (reason.isNotBlank()) parts.add(reason)
    }
    if (payload.has("notes")) {
        val notes = payload.optString("notes")
        if (notes.isNotBlank() && notes !in parts) parts.add(notes)
    }
    if (payload.has("amount")) {
        val amount = payload.opt("amount")?.toString().orEmpty()
        if (amount.isNotBlank()) parts.add(amount)
    }
    if (payload.has("resolution")) {
        val resolution = payload.optString("resolution")
        if (resolution.isNotBlank()) parts.add(resolution)
    }
    if (payload.has("action")) {
        val action = payload.optString("action")
        if (action.isNotBlank()) parts.add(action)
    }
    return parts.joinToString(" · ")
}

private fun shortFingerprint(id: String): String {
    val fp = id.take(9)
    if (fp.length < 9) return fp
    return "${fp.substring(0, 3)}-${fp.substring(3, 6)}-${fp.substring(6, 9)}"
}

// ---- Internal state + ViewModel for the embedded timeline ----

internal data class AuditTimelineUiState(
    val isLoading: Boolean = true,
    val entries: List<AuditEntry> = emptyList(),
    val error: MochiError? = null,
)

@HiltViewModel
internal class AuditTimelineHostViewModel @Inject constructor(
    private val repository: StaffRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AuditTimelineUiState())
    val state: StateFlow<AuditTimelineUiState> = _state.asStateFlow()

    private var lastKey: Pair<String, String>? = null

    fun load(kind: String, objectId: String) {
        val key = kind to objectId
        if (lastKey == key) return
        lastKey = key
        _state.value = AuditTimelineUiState(isLoading = true)
        viewModelScope.launch {
            try {
                val r = repository.getObjectAudit(kind, objectId, limit = 50)
                _state.value = AuditTimelineUiState(
                    isLoading = false,
                    entries = r.audit,
                )
            } catch (e: Exception) {
                _state.value = AuditTimelineUiState(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }
}
