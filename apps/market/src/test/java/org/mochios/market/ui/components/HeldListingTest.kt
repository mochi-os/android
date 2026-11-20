// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.market.R
import org.mochios.market.model.Listing
import org.mochios.market.model.ListingStatus
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A live listing moderation holds is shown to its seller as held, not active,
 * with the reason staff gave; a rejected one as rejected.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HeldListingTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `a held listing reads as held`() {
        assertEquals(ListingStatus.HELD, Listing(status = ListingStatus.ACTIVE, moderation = "hold").shownStatus())
        assertEquals(ListingStatus.ACTIVE, Listing(status = ListingStatus.ACTIVE).shownStatus())
        assertEquals(ListingStatus.REJECTED, Listing(status = ListingStatus.REMOVED, moderation = "rejected").shownStatus())
    }

    @Test
    fun `the seller is told it is held, with staff's reason`() {
        rule.setContent {
            ModerationNotice(Listing(status = ListingStatus.ACTIVE, moderation = "hold", notes = "Photos needed"))
        }
        assertEquals(1, rule.onAllNodesWithText(context.getString(R.string.market_listing_held_title)).fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodesWithText("Photos needed").fetchSemanticsNodes().size)
    }

    @Test
    fun `a listing on sale has no notice`() {
        rule.setContent { ModerationNotice(Listing(status = ListingStatus.ACTIVE, notes = "Approved")) }
        assertEquals(0, rule.onAllNodesWithText("Approved").fetchSemanticsNodes().size)
    }
}
