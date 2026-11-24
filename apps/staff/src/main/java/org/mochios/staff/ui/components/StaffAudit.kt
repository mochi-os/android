// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.components

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import org.json.JSONObject
import org.mochios.android.format.formatFingerprint
import org.mochios.android.format.formatPrice
import org.mochios.android.format.personName
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.staff.R
import org.mochios.staff.model.AuditEntry

/*
 * The audit log's vocabulary, shared by every place the console shows an
 * entry: the per-object timeline in the dialogs and the dashboard's Audit tab.
 * Mirrors `apps/staff/web/src/components/shared/audit-labels.ts`.
 */

/** The label of the audit action [action], or null for one this client does not know. */
@StringRes
internal fun auditActionRes(action: String): Int? = when (action) {
    // Listing
    "listing.created" -> R.string.staff_audit_action_listing_created
    "listing.updated" -> R.string.staff_audit_action_listing_updated
    "listing.deleted" -> R.string.staff_audit_action_listing_deleted
    "listing.published" -> R.string.staff_audit_action_listing_published
    "listing.relisted" -> R.string.staff_audit_action_listing_relisted
    "listing.expired" -> R.string.staff_audit_action_listing_expired
    "listing.removed" -> R.string.staff_audit_action_listing_removed
    "listing.cleared" -> R.string.staff_audit_action_listing_cleared
    "listing.approved" -> R.string.staff_audit_action_listing_approved
    "listing.rejected" -> R.string.staff_audit_action_listing_rejected
    "listing.warning" -> R.string.staff_audit_action_listing_warning
    "listing.appeal_submitted" -> R.string.staff_audit_action_listing_appeal_submitted
    "listing.appeal_decided" -> R.string.staff_audit_action_listing_appeal_decided

    // Account
    "account.seller_activated" -> R.string.staff_audit_action_account_seller_activated
    "account.stripe_connected" -> R.string.staff_audit_action_account_stripe_connected
    "account.stripe_onboarded" -> R.string.staff_audit_action_account_stripe_onboarded
    "account.stripe_restricted" -> R.string.staff_audit_action_account_stripe_restricted
    "account.stripe_disconnected" -> R.string.staff_audit_action_account_stripe_disconnected
    "account.suspended" -> R.string.staff_audit_action_account_suspended
    "account.unsuspended" -> R.string.staff_audit_action_account_unsuspended
    "account.banned" -> R.string.staff_audit_action_account_banned
    "account.unbanned" -> R.string.staff_audit_action_account_unbanned

    // Order
    "order.created" -> R.string.staff_audit_action_order_created
    "order.paid" -> R.string.staff_audit_action_order_paid
    "order.payment_failed" -> R.string.staff_audit_action_order_payment_failed
    "order.shipped" -> R.string.staff_audit_action_order_shipped
    "order.completed" -> R.string.staff_audit_action_order_completed
    "order.refunded" -> R.string.staff_audit_action_order_refunded
    "order.cancelled" -> R.string.staff_audit_action_order_cancelled
    "order.chargeback" -> R.string.staff_audit_action_order_chargeback
    "order.chargeback_won" -> R.string.staff_audit_action_order_chargeback_won
    "order.chargeback_lost" -> R.string.staff_audit_action_order_chargeback_lost
    // A staff member opened someone's order; the read of a conversation
    // below is a different entry, and the one a review of staff conduct most
    // needs to tell apart.
    "order.staff_read" -> R.string.staff_audit_action_order_staff_read
    "thread.staff_read" -> R.string.staff_audit_action_thread_staff_read

    // Dispute
    "dispute.opened" -> R.string.staff_audit_action_dispute_opened
    "dispute.responded" -> R.string.staff_audit_action_dispute_responded
    "dispute.resolved_buyer" -> R.string.staff_audit_action_dispute_resolved_buyer
    "dispute.resolved_seller" -> R.string.staff_audit_action_dispute_resolved_seller

    // Auction
    "auction.created" -> R.string.staff_audit_action_auction_created
    "auction.opened" -> R.string.staff_audit_action_auction_opened
    "auction.bid_placed" -> R.string.staff_audit_action_auction_bid_placed
    "auction.ended_sold" -> R.string.staff_audit_action_auction_ended_sold
    "auction.ended_unsold" -> R.string.staff_audit_action_auction_ended_unsold
    "auction.cancelled" -> R.string.staff_audit_action_auction_cancelled
    "auction.payment_overdue" -> R.string.staff_audit_action_auction_payment_overdue

    // Subscription
    "subscription.created" -> R.string.staff_audit_action_subscription_created
    "subscription.activated" -> R.string.staff_audit_action_subscription_activated
    "subscription.active" -> R.string.staff_audit_action_subscription_active
    "subscription.cancel_scheduled" -> R.string.staff_audit_action_subscription_cancel_scheduled
    "subscription.cancelled" -> R.string.staff_audit_action_subscription_cancelled
    "subscription.paused" -> R.string.staff_audit_action_subscription_paused
    "subscription.resumed" -> R.string.staff_audit_action_subscription_resumed
    "subscription.reactivated" -> R.string.staff_audit_action_subscription_reactivated
    "subscription.past_due" -> R.string.staff_audit_action_subscription_past_due
    "subscription.chargeback" -> R.string.staff_audit_action_subscription_chargeback
    "subscription.chargeback_won" -> R.string.staff_audit_action_subscription_chargeback_won
    "subscription.chargeback_lost" -> R.string.staff_audit_action_subscription_chargeback_lost

    // Payment
    "payment.orphaned" -> R.string.staff_audit_action_payment_orphaned

    // Reports / reviews
    "report.created" -> R.string.staff_audit_action_report_created
    "report.actioned" -> R.string.staff_audit_action_report_actioned
    "review.created" -> R.string.staff_audit_action_review_created
    "review.responded" -> R.string.staff_audit_action_review_responded
    "review.revealed" -> R.string.staff_audit_action_review_revealed
    "review.hide" -> R.string.staff_audit_action_review_hide
    "review.remove" -> R.string.staff_audit_action_review_remove
    "review.restore" -> R.string.staff_audit_action_review_restore

    // Category
    "category.created" -> R.string.staff_audit_action_category_created
    "category.updated" -> R.string.staff_audit_action_category_updated
    "category.deleted" -> R.string.staff_audit_action_category_deleted

    // Staff / config
    "staff.team_added" -> R.string.staff_audit_action_staff_team_added
    "staff.team_removed" -> R.string.staff_audit_action_staff_team_removed
    "staff.team_role_changed" -> R.string.staff_audit_action_staff_team_role_changed
    "config.updated" -> R.string.staff_audit_action_config_updated
    "config.thresholds_updated" -> R.string.staff_audit_action_config_thresholds_updated

    // Audit
    "audit.unknown_kind_probe" -> R.string.staff_audit_action_audit_unknown_kind_probe

    else -> null
}

/** [action] as a translated label; one this client does not know reads as "Other activity", never as its key. */
@Composable
fun auditActionLabel(action: String): String =
    stringResource(auditActionRes(action) ?: R.string.staff_audit_action_other)

/** What kind of thing an audit entry concerns. */
@StringRes
internal fun auditKindRes(kind: String): Int? = when (kind) {
    "listing" -> R.string.staff_reports_type_listing
    "order" -> R.string.staff_audit_kind_order
    "dispute" -> R.string.staff_audit_kind_dispute
    "auction" -> R.string.staff_audit_kind_auction
    "subscription" -> R.string.staff_audit_kind_subscription
    "account" -> R.string.staff_audit_kind_account
    "staff" -> R.string.staff_audit_kind_staff
    "category" -> R.string.staff_audit_kind_category
    "report" -> R.string.staff_reports_dialog_title_view
    "review" -> R.string.staff_audit_kind_review
    "thread" -> R.string.staff_reports_conversation
    "payment" -> R.string.staff_audit_kind_payment
    "config" -> R.string.staff_sidebar_config
    else -> null
}

/**
 * What an entry concerns, for a reader: its kind and the name the server
 * resolved for it (a listing's title, an account's name, or for an account
 * its fingerprint). Never its id, which means nothing to a reader.
 */
@Composable
fun auditObject(entry: AuditEntry): String {
    val kind = stringResource(auditKindRes(entry.kind) ?: R.string.staff_status_unknown)
    val name = entry.objectName.ifBlank { formatFingerprint(entry.objectFingerprint) }
    return if (name.isEmpty()) kind else "$kind · $name"
}

/**
 * Who made an entry: the system, a staff member the Comptroller does not name
 * to this reader, or a person by name, else fingerprint, else "Unknown".
 */
@Composable
fun auditActor(entry: AuditEntry): String = when {
    entry.actor == "system" || entry.actor.isBlank() -> stringResource(R.string.staff_audit_actor_system)
    entry.actor == "staff" && entry.actorName.isBlank() -> stringResource(R.string.staff_audit_actor_staff)
    else -> personName(entry.actorName, entry.actorFingerprint)
}

/** [auditDetail] in the user's language and number format. */
@Composable
fun auditDetail(entry: AuditEntry): String =
    auditDetail(entry.action, entry.data, LocalResources.current, LocalFormat.current)

/**
 * What an entry's data says, in words: its reason, notes and description, the
 * action or decision taken, and its amounts in their currency. Wire values go
 * through the module's tables; a reason that is none of theirs is the words a
 * person wrote. Keys that hold only wire values with no reader's meaning (how
 * a refund was made, a listing's prior status) are left out.
 */
internal fun auditDetail(action: String, data: String, resources: Resources, format: Format): String {
    if (data.isBlank()) return ""
    val payload = try {
        JSONObject(data)
    } catch (_: Exception) {
        return ""
    }
    val parts = mutableListOf<String>()
    fun text(key: String): String = payload.optString(key, "").takeIf { payload.opt(key) is String }.orEmpty().trim()
    fun add(value: String?) {
        if (!value.isNullOrBlank() && value !in parts) parts.add(value)
    }
    fun label(@StringRes res: Int?): String? = res?.let { id -> resources.getString(id) }

    val currency = text("currency")
    fun price(key: String): String? =
        if (payload.opt(key) is Number) formatPrice(payload.optLong(key), currency, format) else null

    val reason = text("reason")
    if (reason.isNotEmpty()) {
        val res = when (action) {
            "dispute.opened" -> disputeReasonRes(reason)
            "order.chargeback", "subscription.chargeback" -> stripeReasonRes(reason)
            "report.created" -> reportReasonRes(reason)
            else -> null
        } ?: systemReasonRes(reason)
            // Acting on a report records the report's reason when staff wrote
            // no notes of their own.
            ?: reportReasonRes(reason)
        add(label(res) ?: reason)
    }
    add(text("notes"))
    add(text("description"))
    add(text("response"))
    add(text("name"))
    add(text("carrier"))
    add(text("tracking"))
    if (action == "report.actioned") add(label(reportActionRes(text("action"))))
    when (text("decision")) {
        "upheld" -> add(resources.getString(R.string.staff_appeals_upheld_toast))
        "denied" -> add(resources.getString(R.string.staff_appeals_denied_toast))
    }
    add(label(text("moderation").takeIf { it.isNotEmpty() }?.let(::staffStatusLabelRes)))
    val previous = label(staffRoleRes(text("previous_role")))
    val role = label(staffRoleRes(text("role")))
    add(if (previous != null && role != null) "$previous → $role" else role)
    // A resolution is resolved to the reader's language by the server; an
    // older server's label key is no sentence to show.
    add(text("resolution").takeUnless { it.startsWith("labels.") })
    if (action == "audit.unknown_kind_probe") add(text("kind"))

    add(price("amount"))
    val refund = price("refund_amount")
    if (refund != null) {
        val total = price("total")
        add(if (payload.optBoolean("partial") && total != null) {
            refund + " " + resources.getString(R.string.staff_disputes_meta_refunded_of, total)
        } else {
            refund
        })
    } else if (payload.opt("amount") !is Number) {
        add(price("total"))
    }
    when (action) {
        "config.thresholds_updated" -> {
            if (payload.opt("low") is Number) {
                add(resources.getString(R.string.staff_config_field_auto_approve) + " · " + format.formatNumber(payload.optLong("low")))
            }
            if (payload.opt("high") is Number) {
                add(resources.getString(R.string.staff_config_field_hold) + " · " + format.formatNumber(payload.optLong("high")))
            }
        }
        "config.updated" -> configKeyRes(text("key"))?.let { key ->
            add(resources.getString(key) + " · " + text("value"))
        }
    }
    return parts.joinToString(" · ")
}

/** The configuration field a `config.updated` entry names. */
@StringRes
private fun configKeyRes(key: String): Int? = when (key) {
    "fee_percent" -> R.string.staff_config_field_platform_fee
    "review_timeout_days" -> R.string.staff_config_field_review_window
    "stripe_publishable_key" -> R.string.staff_config_field_stripe_publishable
    "stripe_secret_key" -> R.string.staff_config_field_stripe_secret
    "stripe_webhook_secret" -> R.string.staff_config_field_stripe_webhook
    "stripe_oauth_client_id" -> R.string.staff_config_field_stripe_oauth_client
    else -> null
}
