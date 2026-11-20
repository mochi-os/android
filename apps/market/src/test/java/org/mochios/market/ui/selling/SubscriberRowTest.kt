// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.selling

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.AppContext
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.market.R
import org.mochios.market.model.Subscription
import org.mochios.market.model.SubscriptionStatus
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A seller's subscriber row says when a cancelled subscription ends, only
 * while it still runs, and names the subscriber by name or fingerprint,
 * never by entity id.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SubscriberRowTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    private val ends = 4_102_444_800L

    /** The line a cancelled subscription ending on [ends] reads, as written in the row's format. */
    private var cancels = ""

    private fun show(subscription: Subscription) {
        AppContext.set(context)
        rule.setContent {
            cancels = context.getString(R.string.market_subscriptions_cancels_on, LocalFormat.current.formatTimestamp(ends))
            SubscriberRow(subscription = subscription, onClickBuyer = {})
        }
        rule.waitForIdle()
    }

    @Test
    fun `a cancelled subscription still running says when it ends`() {
        show(Subscription(id = "s1", buyer = "entity1", buyerName = "Ana", status = SubscriptionStatus.ACTIVE, cancelled = 100, ends = ends))
        rule.onNodeWithText(cancels).assertExists()
    }

    @Test
    fun `an ended subscription says nothing of ending`() {
        show(Subscription(id = "s1", buyer = "entity1", buyerName = "Ana", status = SubscriptionStatus.CANCELLED, cancelled = 100, ends = ends))
        rule.onAllNodesWithText(cancels).assertCountEquals(0)
    }

    @Test
    fun `a subscriber with no name is shown by fingerprint, never by id`() {
        show(Subscription(id = "s1", buyer = "entity1234567890", buyerFingerprint = "abcdefghi", status = SubscriptionStatus.ACTIVE))
        rule.onNodeWithText("abc-def-ghi").assertExists()
        rule.onAllNodesWithText("entity1234567890").assertCountEquals(0)
    }
}
