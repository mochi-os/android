// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.reports

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.AppContext
import org.mochios.staff.R
import org.mochios.staff.model.ConversationMessage
import org.mochios.staff.ui.dialog.ConversationSection
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The report dialog's conversation: a button until loaded, then the messages. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConversationSectionTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun begin() = AppContext.set(context)

    private fun count(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun `an unloaded conversation offers to show it`() {
        var shown = 0
        rule.setContent {
            ConversationSection(messages = null, loading = false, onShow = { shown++ })
        }
        rule.onNode(hasText(context.getString(R.string.staff_reports_conversation_show)) and hasClickAction())
            .performClick()
        assertEquals(1, shown)
    }

    @Test
    fun `a loaded conversation shows its messages`() {
        rule.setContent {
            ConversationSection(
                messages = listOf(
                    ConversationMessage(id = "m1", senderName = "Buyer", body = "Can you get me one?", created = 100),
                    ConversationMessage(id = "m2", senderName = "Seller", body = "No.", created = 200),
                ),
                loading = false,
                onShow = {},
            )
        }
        assertEquals(1, count("Can you get me one?"))
        assertEquals(1, count("No."))
        assertEquals(1, count("Buyer"))
        assertEquals(0, count(context.getString(R.string.staff_reports_conversation_show)))
    }

    @Test
    fun `an empty conversation says so`() {
        rule.setContent {
            ConversationSection(messages = emptyList(), loading = false, onShow = {})
        }
        assertEquals(1, count(context.getString(R.string.staff_reports_conversation_empty)))
    }
}
