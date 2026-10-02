// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.settings.api.Route
import org.mochios.settings.api.TestResult
import org.mochios.settings.ui.domains.routeMeta
import org.mochios.settings.ui.login.RecoveryCodesSection
import org.mochios.settings.ui.notificationprefs.testMessage
import org.mochios.settings.ui.systemusers.PaginationBar
import org.mochios.settings.ui.systemusers.SystemUsersToast
import org.mochios.settings.ui.systemusers.systemUsersToastMessages
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Counts inside sentences used to be written by String.format, which uses the
 * language's own digits (Arabic-Indic in Arabic) and no grouping. They are
 * written as the user writes numbers, digits 0 to 9, as the web writes them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ar")
class CountFormatTest {

    @get:Rule
    val rule = createComposeRule()

    private fun assertWritten(text: String, vararg numbers: String) {
        numbers.forEach { number -> assertTrue(text, number in text) }
        assertTrue(text, text.none { it in '٠'..'٩' })
    }

    @Test
    fun `the users page range and total`() {
        rule.setContent { PaginationBar(offset = 1000, limit = 1000, count = 12345, onLimit = {}, onPrev = {}, onNext = {}) }
        rule.onNodeWithText("1,001", substring = true).assertExists()
        rule.onNodeWithText("2,000", substring = true).assertExists()
        rule.onNodeWithText("12,345", substring = true).assertExists()
    }

    @Test
    fun `the sessions revoked toast`() {
        var text = ""
        rule.setContent { text = systemUsersToastMessages(12345).getValue(SystemUsersToast.SESSIONS_REVOKED) }
        rule.waitForIdle()
        assertWritten(text, "12,345")
    }

    @Test
    fun `the recovery codes remaining`() {
        rule.setContent { RecoveryCodesSection(count = 1234, onGenerate = {}) }
        rule.onNodeWithText("1,234", substring = true).assertExists()
    }

    @Test
    fun `a test send's reach`() {
        var texts: List<String> = emptyList()
        rule.setContent { texts = listOf(testMessage(TestResult(sent = 1000, total = 12345)), testMessage(TestResult(sent = 2000, total = 2000))) }
        rule.waitForIdle()
        assertWritten(texts[0], "1,000", "12,345")
        assertWritten(texts[1], "2,000")
    }

    @Test
    fun `a route's priority`() {
        var text = ""
        rule.setContent { text = routeMeta(Route(priority = 12345, enabled = 1)) }
        rule.waitForIdle()
        assertWritten(text, "12,345")
    }
}
