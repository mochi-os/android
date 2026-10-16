// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.MochiCard
import org.mochios.market.R
import org.mochios.market.model.AuditEvent

/**
 * The wire action key as a translated label; mirrors web's `useActionLabels`.
 * An action the map does not know renders as its key, as on web.
 */
@Composable
fun auditActionLabel(action: String): String {
    val resource = when (action) {
        "order.created" -> R.string.market_audit_order_created
        "order.paid" -> R.string.market_audit_order_paid
        "order.payment_failed" -> R.string.market_audit_order_payment_failed
        "order.shipped" -> R.string.market_audit_order_shipped
        "order.completed" -> R.string.market_audit_order_completed
        "order.refunded" -> R.string.market_audit_order_refunded
        "order.cancelled" -> R.string.market_audit_order_cancelled
        "order.chargeback" -> R.string.market_audit_order_chargeback
        "order.chargeback_won" -> R.string.market_audit_order_chargeback_won
        "order.chargeback_lost" -> R.string.market_audit_order_chargeback_lost
        "dispute.opened" -> R.string.market_audit_dispute_opened
        "dispute.responded" -> R.string.market_audit_dispute_responded
        "dispute.resolved_buyer" -> R.string.market_audit_dispute_resolved_buyer
        "dispute.resolved_seller" -> R.string.market_audit_dispute_resolved_seller
        "listing.created" -> R.string.market_audit_listing_created
        "listing.updated" -> R.string.market_audit_listing_updated
        "listing.deleted" -> R.string.market_audit_listing_deleted
        "listing.published" -> R.string.market_audit_listing_published
        "listing.relisted" -> R.string.market_audit_listing_relisted
        "listing.expired" -> R.string.market_audit_listing_expired
        "listing.removed" -> R.string.market_audit_listing_removed
        "listing.cleared" -> R.string.market_audit_listing_cleared
        "listing.approved" -> R.string.market_audit_listing_approved
        "listing.rejected" -> R.string.market_audit_listing_rejected
        "listing.warning" -> R.string.market_audit_listing_warning
        "listing.appeal_submitted" -> R.string.market_audit_listing_appeal_submitted
        "listing.appeal_decided" -> R.string.market_audit_listing_appeal_decided
        "auction.created" -> R.string.market_audit_auction_created
        "auction.opened" -> R.string.market_audit_auction_opened
        "auction.bid_placed" -> R.string.market_audit_auction_bid_placed
        "auction.ended_sold" -> R.string.market_audit_auction_ended_sold
        "auction.ended_unsold" -> R.string.market_audit_auction_ended_unsold
        "auction.payment_overdue" -> R.string.market_audit_auction_payment_overdue
        "subscription.created" -> R.string.market_audit_subscription_created
        "subscription.activated" -> R.string.market_audit_subscription_activated
        "subscription.cancel_scheduled" -> R.string.market_audit_subscription_cancel_scheduled
        "subscription.cancelled" -> R.string.market_audit_subscription_cancelled
        "subscription.paused" -> R.string.market_audit_subscription_paused
        "subscription.resumed" -> R.string.market_audit_subscription_resumed
        "subscription.reactivated" -> R.string.market_audit_subscription_reactivated
        "subscription.past_due" -> R.string.market_audit_subscription_past_due
        "subscription.chargeback" -> R.string.market_audit_subscription_chargeback
        "subscription.chargeback_won" -> R.string.market_audit_subscription_chargeback_won
        "subscription.chargeback_lost" -> R.string.market_audit_subscription_chargeback_lost
        "review.created" -> R.string.market_audit_review_created
        "review.responded" -> R.string.market_audit_review_responded
        "review.revealed" -> R.string.market_audit_review_revealed
        "review.hide" -> R.string.market_audit_review_hide
        "review.remove" -> R.string.market_audit_review_remove
        "review.restore" -> R.string.market_audit_review_restore
        "report.created" -> R.string.market_audit_report_created
        "report.actioned" -> R.string.market_audit_report_actioned
        "account.seller_activated" -> R.string.market_audit_account_seller_activated
        "account.stripe_connected" -> R.string.market_audit_account_stripe_connected
        "account.stripe_onboarded" -> R.string.market_audit_account_stripe_onboarded
        "account.stripe_restricted" -> R.string.market_audit_account_stripe_restricted
        "account.stripe_disconnected" -> R.string.market_audit_account_stripe_disconnected
        "account.suspended" -> R.string.market_audit_account_suspended
        "account.unsuspended" -> R.string.market_audit_account_unsuspended
        "account.banned" -> R.string.market_audit_account_banned
        "account.unbanned" -> R.string.market_audit_account_unbanned
        else -> null
    }
    return if (resource != null) stringResource(resource) else action
}

/**
 * [events] in a card under a History heading, newest first as the server sends
 * them; draws nothing when there are none, since a viewer without read access
 * gets an empty list.
 */
@Composable
fun AuditHistoryCard(
    events: List<AuditEvent>,
    modifier: Modifier = Modifier,
) {
    if (events.isEmpty()) {
        return
    }
    MochiCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.market_audit_history),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            AuditTimeline(events = events)
        }
    }
}

/**
 * [events] as a timeline: a dot per event with a line joining it to the next,
 * the action's label beside it and the time and actor under that.
 */
@Composable
fun AuditTimeline(
    events: List<AuditEvent>,
    modifier: Modifier = Modifier,
) {
    if (events.isEmpty()) {
        return
    }
    val format = LocalFormat.current
    Column(modifier = modifier) {
        events.forEachIndexed { index, event ->
            val last = index == events.lastIndex
            val timestampText = if (event.timestamp > 0L) {
                format.formatTimestamp(event.timestamp)
            } else {
                ""
            }
            val sub = listOf(timestampText, event.actorName)
                .filter { part -> part.isNotBlank() }
                .joinToString(" · ")
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(10.dp)
                        .fillMaxHeight(),
                ) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    if (!last) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .width(1.dp)
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp, bottom = if (last) 0.dp else 16.dp),
                ) {
                    Text(
                        text = auditActionLabel(event.action),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (sub.isNotEmpty()) {
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
