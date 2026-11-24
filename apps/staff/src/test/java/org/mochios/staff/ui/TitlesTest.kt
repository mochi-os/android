// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.staff.R
import org.mochios.staff.StaffServer
import org.mochios.staff.StaffServer.Companion.ok
import org.mochios.staff.model.Appeal
import org.mochios.staff.model.PendingListing
import org.mochios.staff.model.Report
import org.mochios.staff.ui.dialog.AppealDecideDialog
import org.mochios.staff.ui.dialog.ListingActionDialog
import org.mochios.staff.ui.listings.ListingActionType
import org.mochios.staff.ui.listings.PendingListingAction
import org.mochios.staff.ui.reports.ReportRow
import org.mochios.staff.ui.reviews.ReviewsScreen
import org.mochios.staff.ui.reviews.ReviewsViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Things are named, never numbered: a listing by its title or "Untitled
 * listing", a report by what it is about in words, a review by its people and
 * listing; no Comptroller uid reaches the screen, and the review-removal
 * warning says only what happens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TitlesTest {

    @get:Rule
    val rule = createComposeRule()

    private val uid = "2aBcDeFgHiJkLmNoPqRsTuVwXyZ0123456789"

    private val server = StaffServer { request ->
        val path = request.requestUrl!!.encodedPath
        when {
            path.endsWith("-/reviews/list") -> ok(
                """{"reviews": [{"id": "v1", "order": "$uid", "reviewer": "u1", "reviewer_name": "Ann", "subject": "u2",
                   "subject_name": "Bob", "listing_title": "Teapot", "status": "published", "role": "buyer"}], "total": 1, "next": ""}""",
            )
            else -> null
        }
    }

    @Before
    fun begin() {
        server.start()
    }

    @After
    fun end() = server.stop()

    private fun text(id: Int) = server.context.getString(id)

    @Test
    fun `a listing action is headed by the listing's title alone`() {
        rule.setContent {
            ListingActionDialog(
                action = PendingListingAction(ListingActionType.APPROVE, PendingListing(id = uid, title = "Teapot")),
                submitting = false,
                onDismiss = {},
                onSubmit = { _, _ -> },
                history = {},
            )
        }
        rule.onNodeWithText("Teapot").assertExists()
        rule.onAllNodesWithText(uid, substring = true).assertCountEquals(0)
    }

    @Test
    fun `an untitled appeal is an untitled listing`() {
        rule.setContent {
            AppealDecideDialog(appeal = Appeal(id = "a1", listing = uid), submitting = false, onDismiss = {}, onSubmit = { _, _ -> }, history = {})
        }
        rule.onNodeWithText(text(R.string.staff_listing_untitled)).assertExists()
        rule.onAllNodesWithText(uid, substring = true).assertCountEquals(0)
    }

    @Test
    fun `a report says what it is about in words`() {
        rule.setContent {
            ReportRow(report = Report(id = "r1", type = "user", target = uid, targetName = "Bob", status = "pending"), onOpenListing = {}, onActionClick = {})
        }
        rule.onNodeWithText(text(R.string.staff_reports_type_user)).assertExists()
        rule.onAllNodesWithText("user").assertCountEquals(0)
    }

    @Test
    fun `a reported listing with no title is an untitled listing`() {
        rule.setContent {
            ReportRow(report = Report(id = "r1", type = "listing", target = uid, status = "pending"), onOpenListing = {}, onActionClick = {})
        }
        rule.onNodeWithText(text(R.string.staff_listing_untitled)).assertExists()
        rule.onAllNodesWithText(uid, substring = true).assertCountEquals(0)
    }

    @Test
    fun `a review card shows no order number, and its removal warns of no permanence`() {
        val model = ReviewsViewModel(server.repository, SavedStateHandle())
        rule.setContent { ReviewsScreen(viewModel = model) }
        server.until { model.state.value.reviews.isNotEmpty() }
        rule.waitForIdle()
        rule.onNodeWithText("Teapot").assertExists()
        rule.onAllNodesWithText(uid, substring = true).assertCountEquals(0)
        model.askRemove(model.state.value.reviews.single())
        rule.waitForIdle()
        rule.onNodeWithText("Ann → Bob on Teapot").assertExists()
        rule.onAllNodesWithText("Permanently", substring = true).assertCountEquals(0)
    }
}
