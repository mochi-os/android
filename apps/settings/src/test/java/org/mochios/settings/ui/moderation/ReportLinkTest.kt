// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.moderation

import android.content.Intent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.AppContext
import org.mochios.settings.api.Report
import org.mochios.settings.api.ReportParty
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** A link in a report opens in the browser, and a device with none does not close the app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReportLinkTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val link = "https://remote.example/notes/1"
    private val report = Report(
        id = "r1",
        network = "fediverse",
        reporter = ReportParty(id = "https://remote.example/actor", name = "@mod@remote.example"),
        objects = listOf(link),
        created = 1,
    )

    @Before
    fun begin() {
        AppContext.set(context)
        // Starting an activity nothing handles throws, as on a device with no browser.
        shadowOf(context).checkActivities(true)
    }

    /** The link was opened through Compose's UriHandler, which rethrows the refusal and closes the app. */
    @Test
    fun `a link tapped with no browser is dropped rather than closing the app`() {
        rule.setContent { ReportCard(report = report, busy = false, onDismiss = {}, onAction = {}) }
        rule.onNodeWithText(link).performClick()
        rule.waitForIdle()
    }

    @Test
    fun `a link tapped opens in the browser`() {
        shadowOf(context).checkActivities(false)
        rule.setContent { ReportCard(report = report, busy = false, onDismiss = {}, onAction = {}) }
        rule.onNodeWithText(link).performClick()
        rule.waitForIdle()
        val started = shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(link, started.dataString)
    }
}
