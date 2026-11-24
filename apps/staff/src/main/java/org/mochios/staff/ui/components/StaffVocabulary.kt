// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.mochios.staff.R

/**
 * The values the Comptroller stores, column by column. The console's filters
 * offer these and nothing else, and every one of them has a label, so a filter
 * cannot name a value no row holds and a row cannot show its wire value.
 */
object Stored {
    object Order {
        val status = listOf("paid", "shipped", "delivered", "completed", "disputed", "refunded", "cancelled")
    }

    object Listing {
        val status = listOf("draft", "active", "sold", "expired", "removed")

        /** A draft's moderation is "", which is no value and has no label. */
        val moderation = listOf("approved", "review", "hold", "manual", "rejected")
    }

    object Moderation {
        val action = listOf(
            "approved", "review", "hold", "manual", "rejected", "removed", "appealed", "warning", "upheld", "denied",
        )
    }

    object Report {
        val status = listOf("pending", "actioned", "dismissed")
        val type = listOf("listing", "user")
    }

    object Dispute {
        val status = listOf("open", "responded", "resolved_buyer", "resolved_seller")
    }

    object Review {
        val status = listOf("published", "removed")
    }

    object Account {
        val status = listOf("active", "suspended", "banned")
    }

    object Category {
        val state = listOf("active", "inactive")
    }

    object Staff {
        val role = listOf("admin", "moderator", "support")
    }
}

/** The listings filter's statuses, in [Stored.Listing.status]'s order. */
internal val listingStatusLabels: Map<String, Int> = linkedMapOf(
    "draft" to R.string.staff_listings_status_draft,
    "active" to R.string.staff_listings_status_active,
    "sold" to R.string.staff_listings_status_sold,
    "expired" to R.string.staff_listings_status_expired,
    "removed" to R.string.staff_listings_status_removed,
)

/** The listings filter's moderation states, in [Stored.Listing.moderation]'s order. */
internal val listingModerationLabels: Map<String, Int> = linkedMapOf(
    "approved" to R.string.staff_status_approved,
    "review" to R.string.staff_listings_moderation_review,
    "hold" to R.string.staff_listings_moderation_hold,
    "manual" to R.string.staff_listings_moderation_manual,
    "rejected" to R.string.staff_listings_moderation_rejected,
)

/** The reports filter's statuses, in [Stored.Report.status]'s order. */
internal val reportStatusLabels: Map<String, Int> = linkedMapOf(
    "pending" to R.string.staff_reports_status_pending,
    "actioned" to R.string.staff_reports_status_actioned,
    "dismissed" to R.string.staff_reports_status_dismissed,
)

/** A report's types, in [Stored.Report.type]'s order: the filter's options and the card's label. */
internal val reportTypeLabels: Map<String, Int> = linkedMapOf(
    "listing" to R.string.staff_reports_type_listing,
    "user" to R.string.staff_reports_type_user,
)

/** The disputes filter's statuses, in [Stored.Dispute.status]'s order. */
internal val disputeStatusLabels: Map<String, Int> = linkedMapOf(
    "open" to R.string.staff_disputes_status_open,
    "responded" to R.string.staff_disputes_status_responded,
    "resolved_buyer" to R.string.staff_disputes_status_resolved_buyer,
    "resolved_seller" to R.string.staff_disputes_status_resolved_seller,
)

/** [labels] as a filter's options: each value with its label, in the table's order. */
@Composable
internal fun filterOptions(labels: Map<String, Int>): List<Pair<String, String>> =
    labels.map { (value, label) -> value to stringResource(label) }

/** What a report is about, translated; "Unknown" for a type this client does not know. */
@Composable
internal fun reportTypeLabel(type: String): String =
    stringResource(reportTypeLabels[type] ?: R.string.staff_status_unknown)

/** A report's reason, by the reasons a reporter picks from; anything else is the reporter's own words. */
@StringRes
internal fun reportReasonRes(reason: String): Int? = when (reason) {
    "prohibited" -> R.string.staff_reports_reason_prohibited
    "counterfeit" -> R.string.staff_reports_reason_counterfeit
    "misleading" -> R.string.staff_reports_reason_misleading
    "inappropriate" -> R.string.staff_reports_reason_inappropriate
    "spam" -> R.string.staff_reports_reason_spam
    "other" -> R.string.staff_reports_reason_other
    else -> null
}

@Composable
internal fun reportReasonLabel(reason: String): String =
    reportReasonRes(reason)?.let { label -> stringResource(label) } ?: reason

/** What staff did about a report, by the actions the report dialog offers. */
@StringRes
internal fun reportActionRes(action: String): Int? = when (action) {
    "dismiss" -> R.string.staff_reports_action_dismiss
    "warn" -> R.string.staff_reports_action_warn
    "remove" -> R.string.staff_reports_action_remove
    "suspend" -> R.string.staff_reports_action_suspend
    "ban" -> R.string.staff_reports_action_ban
    else -> null
}

/** A buyer's reason for a dispute; anything else is the buyer's own words. */
@StringRes
internal fun disputeReasonRes(reason: String): Int? = when (reason) {
    "not_received" -> R.string.staff_disputes_reason_not_received
    "not_as_described" -> R.string.staff_disputes_reason_not_as_described
    "damaged" -> R.string.staff_disputes_reason_damaged
    "unauthorised" -> R.string.staff_disputes_reason_unauthorised
    "other" -> R.string.staff_disputes_reason_other
    else -> null
}

/** Stripe's reason code for a chargeback. */
@StringRes
internal fun stripeReasonRes(reason: String): Int? = when (reason) {
    "bank_cannot_process" -> R.string.staff_disputes_stripe_bank_cannot_process
    "check_returned" -> R.string.staff_disputes_stripe_check_returned
    "credit_not_processed" -> R.string.staff_disputes_stripe_credit_not_processed
    "customer_initiated" -> R.string.staff_disputes_stripe_customer_initiated
    "debit_not_authorized" -> R.string.staff_disputes_stripe_debit_not_authorized
    "duplicate" -> R.string.staff_disputes_stripe_duplicate
    "fraudulent" -> R.string.staff_disputes_stripe_fraudulent
    "general" -> R.string.staff_disputes_stripe_general
    "incorrect_account_details" -> R.string.staff_disputes_stripe_incorrect_account_details
    "insufficient_funds" -> R.string.staff_disputes_stripe_insufficient_funds
    "product_not_received" -> R.string.staff_disputes_stripe_product_not_received
    "product_unacceptable" -> R.string.staff_disputes_stripe_product_unacceptable
    "subscription_canceled" -> R.string.staff_disputes_stripe_subscription_canceled
    "unrecognized" -> R.string.staff_disputes_stripe_unrecognized
    else -> null
}

/** The reasons the Comptroller itself gives when it ends an auction, a subscription or a Stripe link. */
@StringRes
internal fun systemReasonRes(reason: String): Int? = when (reason) {
    "checkout_expired" -> R.string.staff_audit_reason_checkout_expired
    "period_ended" -> R.string.staff_audit_reason_period_ended
    "no_bids" -> R.string.staff_audit_reason_no_bids
    "reserve_not_met" -> R.string.staff_audit_reason_reserve_not_met
    "no_winning_bid" -> R.string.staff_audit_reason_no_winning_bid
    "deauthorized" -> R.string.staff_audit_reason_deauthorized
    else -> null
}

/** A staff member's role. */
@StringRes
internal fun staffRoleRes(role: String): Int? = when (role) {
    "admin" -> R.string.staff_team_role_admin
    "moderator" -> R.string.staff_team_role_moderator
    "support" -> R.string.staff_team_role_support
    else -> null
}

/** A listing's condition, by the conditions a seller picks from. */
@StringRes
internal fun conditionRes(condition: String): Int? = when (condition) {
    "new" -> R.string.staff_listing_condition_new
    "used" -> R.string.staff_listing_condition_used
    "refurbished" -> R.string.staff_listing_condition_refurbished
    else -> null
}
