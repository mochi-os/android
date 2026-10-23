// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.reports

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.AppContext
import org.mochios.staff.R
import org.mochios.staff.model.Report
import org.mochios.staff.ui.dialog.MetadataCard
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * What a reporter wrote reaches the moderator: on the report's card in the
 * list, under its reason, and in the report's summary when it is opened.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReportDetailsTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    private fun report(details: String = "", reason: String = "other") = Report(
        id = "r1",
        type = "user",
        target = "u1",
        targetName = "Test Seller",
        reporter = "u2",
        reporterName = "Duc",
        reason = reason,
        details = details,
        status = "pending",
        created = 100,
    )

    private fun card(report: Report) {
        AppContext.set(context)
        rule.setContent { ReportRow(report = report, onOpenListing = {}, onActionClick = {}) }
        rule.waitForIdle()
    }

    @Test
    fun `a report's card shows the reporter's details under the reason`() {
        card(report(details = "dangerous topic"))
        rule.onNodeWithText(context.getString(R.string.staff_reports_reason_other)).assertIsDisplayed()
        rule.onNodeWithText("dangerous topic").assertIsDisplayed()
    }

    @Test
    fun `a report with no details shows its reason alone`() {
        card(report(reason = "spam"))
        rule.onNodeWithText(context.getString(R.string.staff_reports_reason_spam)).assertIsDisplayed()
        rule.onAllNodesWithText(context.getString(R.string.staff_reports_meta_details)).assertCountEquals(0)
    }

    @Test
    fun `an opened report's summary shows the reporter's details`() {
        AppContext.set(context)
        rule.setContent { MetadataCard(report = report(details = "dangerous topic")) }
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(R.string.staff_reports_meta_details)).assertIsDisplayed()
        rule.onNodeWithText("dangerous topic").assertIsDisplayed()
    }
}
