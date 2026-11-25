// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.components

import android.content.res.Resources
import androidx.annotation.StringRes
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.json.JSONException
import org.json.JSONObject
import org.mochios.android.format.formatPrice
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.MochiCard
import org.mochios.market.R
import org.mochios.market.model.AuditEvent

/**
 * The wire action key as a translated label; mirrors web's `useActionLabels`.
 * An action the map does not know reads as "Other activity", never as its key.
 */
@Composable
fun auditActionLabel(action: String): String =
    stringResource(auditActionResource(action) ?: R.string.market_audit_other)

/** The label for the wire action key [action], or null for one this client does not know. */
internal fun auditActionResource(action: String): Int? {
    return when (action) {
        "order.created" -> R.string.market_audit_order_created
        "order.paid" -> R.string.market_audit_order_paid
        "order.shipped" -> R.string.market_audit_order_shipped
        "order.completed" -> R.string.market_audit_order_completed
        "order.refunded" -> R.string.market_audit_order_refunded
        "order.refund_failed" -> R.string.market_audit_order_refund_failed
        "order.chargeback" -> R.string.market_audit_order_chargeback
        "order.chargeback_won" -> R.string.market_audit_order_chargeback_won
        "order.chargeback_lost" -> R.string.market_audit_order_chargeback_lost
        "order.staff_read" -> R.string.market_audit_order_staff_read
        "dispute.opened" -> R.string.market_audit_dispute_opened
        "dispute.responded" -> R.string.market_audit_dispute_responded
        "dispute.resolved_buyer" -> R.string.market_audit_dispute_resolved_buyer
        "dispute.resolved_seller" -> R.string.market_audit_dispute_resolved_seller
        "listing.created" -> R.string.market_audit_listing_created
        "listing.deleted" -> R.string.market_audit_listing_deleted
        "listing.published" -> R.string.market_audit_listing_published
        "listing.relisted" -> R.string.market_audit_listing_relisted
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
        "auction.cancelled" -> R.string.market_audit_auction_cancelled
        "subscription.created" -> R.string.market_audit_subscription_created
        // The webhook writes the status Stripe reports, which for a
        // subscription starting again is "active".
        "subscription.activated", "subscription.active" -> R.string.market_audit_subscription_activated
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
}

/** [auditDetail] for [event], in the user's language and number format. */
@Composable
internal fun auditDetail(event: AuditEvent): String? =
    auditDetail(event.action, event.data, LocalResources.current, LocalFormat.current)

/**
 * The detail shown after an entry's action label, from its [data]: its
 * reason, amount and refund in their currency, notes, description, carrier
 * and tracking, a report's action, an appeal's decision, a listing's
 * moderation and how the action came about; null when it holds none of these.
 * Mirrors web `formatAuditDetail`, in its order. A wire value this client has
 * no words for is left out, never shown as itself.
 */
internal fun auditDetail(action: String, data: String, resources: Resources, format: Format): String? {
    if (data.isBlank()) return null
    val payload = try {
        JSONObject(data)
    } catch (_: JSONException) {
        return null
    }
    fun text(key: String): String = (payload.opt(key) as? String).orEmpty()
    fun number(key: String): Long? = (payload.opt(key) as? Number)?.toLong()
    fun label(@StringRes res: Int?): String? = res?.let { id -> resources.getString(id) }

    val bits = mutableListOf<String>()
    auditReason(action, text("reason"), resources)?.let { reason -> bits += reason }
    val currency = text("currency")
    if (currency.isNotEmpty()) {
        number("amount")?.let { amount -> bits += formatPrice(amount, currency, format) }
        number("refund_amount")?.let { refund ->
            var words = formatPrice(refund, currency, format)
            val total = number("total")
            if (payload.opt("partial") == true && total != null) {
                words += " " + resources.getString(R.string.market_audit_refund_of, formatPrice(total, currency, format))
            }
            bits += words
        }
    }
    text("notes").takeIf { notes -> notes.isNotEmpty() }?.let { notes -> bits += notes }
    text("description").takeIf { description -> description.isNotEmpty() }?.let { description -> bits += description }
    val carrier = text("carrier")
    if (carrier.isNotEmpty()) {
        val tracking = text("tracking")
        bits += if (tracking.isNotEmpty()) "$carrier: $tracking" else carrier
    }
    // Only a report's action is a decision a reader is told of, and only in
    // words.
    if (action == "report.actioned") label(reportActionRes(text("action")))?.let { words -> bits += words }
    when (text("decision")) {
        "upheld" -> bits += resources.getString(R.string.market_audit_appeal_upheld)
        "denied" -> bits += resources.getString(R.string.market_audit_appeal_denied)
    }
    label(moderationRes(text("moderation")))?.let { moderation ->
        bits += resources.getString(R.string.market_audit_moderation, moderation)
    }
    label(viaRes(text("via")))?.let { via -> bits += via }
    return if (bits.isEmpty()) null else bits.joinToString(" — ")
}

/**
 * Actions whose reason is what a person wrote: a reporter, staff removing,
 * rejecting or sanctioning, or a seller appealing. Acting on a report without
 * notes of its own records the report's reason instead.
 */
private val writtenReasons = setOf(
    "account.banned",
    "account.suspended",
    "listing.appeal_submitted",
    "listing.rejected",
    "listing.removed",
    "listing.warning",
    "report.created",
)

/**
 * An entry's [reason] in words: a token by the action it is written with, or
 * on an action whose reason a person writes, their words. A token this client
 * does not know is null, never itself.
 */
internal fun auditReason(action: String, reason: String, resources: Resources): String? {
    if (reason.isEmpty()) return null
    auditReasonRes(action, reason)?.let { res -> return resources.getString(res) }
    if (action in writtenReasons) return reportReasonRes(reason)?.let { res -> resources.getString(res) } ?: reason
    return null
}

/** The reason tokens each action is written with, as the Comptroller writes them. */
@StringRes
internal fun auditReasonRes(action: String, reason: String): Int? = when (action) {
    "dispute.opened" -> refundReasonRes(reason)
    "order.chargeback", "subscription.chargeback" -> chargebackReasonRes(reason)
    "auction.ended_unsold" -> when (reason) {
        "no_bids" -> R.string.market_audit_reason_no_bids
        "reserve_not_met" -> R.string.market_audit_reason_reserve_not_met
        "no_winning_bid" -> R.string.market_audit_reason_no_winning_bid
        else -> null
    }
    "auction.cancelled" -> when (reason) {
        "listing_removed" -> R.string.market_audit_reason_listing_removed
        "payment_unavailable" -> R.string.market_audit_reason_payment_unavailable
        else -> null
    }
    "subscription.cancelled" -> when (reason) {
        "checkout_expired" -> R.string.market_audit_reason_checkout_expired
        "period_ended" -> R.string.market_audit_reason_period_ended
        else -> null
    }
    "subscription.cancel_scheduled" -> if (reason == "listing_removed") R.string.market_audit_reason_listing_removed else null
    "account.stripe_disconnected" -> when (reason) {
        "deauthorized" -> R.string.market_audit_reason_deauthorized
        "unreachable" -> R.string.market_audit_reason_unreachable
        "duplicate" -> R.string.market_audit_reason_duplicate
        else -> null
    }
    "review.revealed" -> if (reason == "timeout") R.string.market_audit_reason_timeout else null
    else -> null
}

/** A report's reason, by the reasons a reporter picks from. */
@StringRes
private fun reportReasonRes(reason: String): Int? = when (reason) {
    "prohibited" -> R.string.market_report_reason_prohibited
    "counterfeit" -> R.string.market_report_reason_counterfeit
    "misleading" -> R.string.market_report_reason_misleading
    "inappropriate" -> R.string.market_report_reason_inappropriate
    "spam" -> R.string.market_report_reason_spam
    "other" -> R.string.market_report_reason_other
    else -> null
}

/** What staff did about a report. */
@StringRes
private fun reportActionRes(action: String): Int? = when (action) {
    "dismiss" -> R.string.market_audit_report_dismiss
    "warn" -> R.string.market_audit_report_warn
    "remove" -> R.string.market_audit_reason_listing_removed
    "suspend" -> R.string.market_audit_account_suspended
    "ban" -> R.string.market_audit_report_ban
    else -> null
}

/** A listing's moderation state, as the Comptroller stores it. */
@StringRes
private fun moderationRes(moderation: String): Int? = when (moderation) {
    "approved" -> R.string.market_audit_moderation_approved
    "review" -> R.string.market_audit_moderation_review
    "hold" -> R.string.market_audit_moderation_hold
    "manual" -> R.string.market_audit_listing_approved
    "rejected" -> R.string.market_audit_moderation_rejected
    else -> null
}

/** How an action came about, where the Comptroller records it. */
@StringRes
internal fun viaRes(via: String): Int? = when (via) {
    "seller" -> R.string.market_audit_via_seller
    "staff" -> R.string.market_audit_via_staff
    "stripe" -> R.string.market_audit_via_stripe
    "reconciled" -> R.string.market_audit_via_reconciled
    "refund" -> R.string.market_audit_via_refund
    "automatic" -> R.string.market_audit_via_automatic
    "withdrawn" -> R.string.market_dispute_withdrawn_by_buyer
    "dispute_resolved_seller" -> R.string.market_dispute_status_resolved_seller
    "stripe_oauth" -> R.string.market_audit_account_stripe_connected
    "instant" -> R.string.market_audit_via_instant
    else -> null
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
 * the action's label and its detail beside it and the time and actor under
 * that.
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
            // The Comptroller names no staff member to a party: such an entry
            // is the staff's, with no name.
            val actor = if (event.actor == "staff" && event.actorName.isBlank()) {
                stringResource(R.string.market_audit_actor_staff)
            } else {
                event.actorName
            }
            val sub = listOf(timestampText, actor)
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
                    val label = auditActionLabel(event.action)
                    val detail = auditDetail(event)
                    val muted = MaterialTheme.colorScheme.onSurfaceVariant
                    Text(
                        text = buildAnnotatedString {
                            append(label)
                            if (detail != null) {
                                withStyle(SpanStyle(color = muted)) { append(": $detail") }
                            }
                        },
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
