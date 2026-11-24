// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The console's filters offer exactly the values the Comptroller stores, and
 * every stored value has a label of its own. The sets below are copied from
 * the Comptroller's (`VALID_*`, `MODERATION_ACTIONS` and the transitions that
 * write them); a value added there and not here fails this test.
 */
class StoredValuesTest {

    private val orderStatus = listOf("paid", "shipped", "delivered", "completed", "disputed", "refunded", "cancelled")
    private val listingStatus = listOf("draft", "active", "sold", "expired", "removed")
    private val listingModeration = listOf("approved", "review", "hold", "manual", "rejected")
    private val moderationAction = listOf(
        "approved", "review", "hold", "manual", "rejected", "removed", "appealed", "warning", "upheld", "denied",
    )
    private val reportStatus = listOf("pending", "actioned", "dismissed")
    private val reportType = listOf("listing", "user")
    private val disputeStatus = listOf("open", "responded", "resolved_buyer", "resolved_seller")
    private val reviewStatus = listOf("published", "removed")
    private val accountStatus = listOf("active", "suspended", "banned")
    private val categoryState = listOf("active", "inactive")
    private val staffRole = listOf("admin", "moderator", "support")

    @Test
    fun `the stored sets are the Comptroller's`() {
        assertEquals(orderStatus, Stored.Order.status)
        assertEquals(listingStatus, Stored.Listing.status)
        assertEquals(listingModeration, Stored.Listing.moderation)
        assertEquals(moderationAction, Stored.Moderation.action)
        assertEquals(reportStatus, Stored.Report.status)
        assertEquals(reportType, Stored.Report.type)
        assertEquals(disputeStatus, Stored.Dispute.status)
        assertEquals(reviewStatus, Stored.Review.status)
        assertEquals(accountStatus, Stored.Account.status)
        assertEquals(categoryState, Stored.Category.state)
        assertEquals(staffRole, Stored.Staff.role)
    }

    @Test
    fun `each filter offers exactly its column's stored values`() {
        assertEquals(listingStatus, listingStatusLabels.keys.toList())
        assertEquals(listingModeration, listingModerationLabels.keys.toList())
        assertEquals(reportStatus, reportStatusLabels.keys.toList())
        assertEquals(reportType, reportTypeLabels.keys.toList())
        assertEquals(disputeStatus, disputeStatusLabels.keys.toList())
    }

    @Test
    fun `every stored value has a badge label`() {
        val shown = orderStatus + listingStatus + listingModeration + moderationAction + reportStatus +
            disputeStatus + reviewStatus + accountStatus + categoryState + staffRole
        for (value in shown) {
            assertNotNull("no label for $value", staffStatusLabelRes(value))
        }
    }

    @Test
    fun `an appeal's decision badge reads as a status, as the web's does, not as the toast`() {
        assertEquals(
            org.mochios.staff.R.string.staff_status_upheld to org.mochios.staff.R.string.staff_status_denied,
            staffStatusLabelRes("upheld") to staffStatusLabelRes("denied"),
        )
    }

    @Test
    fun `values no row holds have no label`() {
        for (value in listOf("auto_approved", "soft_approved", "reviewed", "reviewing", "escalated", "hidden")) {
            assertNull("a label for $value", staffStatusLabelRes(value))
        }
    }
}
