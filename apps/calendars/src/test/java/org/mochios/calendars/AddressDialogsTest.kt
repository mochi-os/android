// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.R as MochiR
import org.mochios.calendars.model.Calendar
import org.mochios.calendars.ui.calendar.AddressDialogs
import org.mochios.calendars.ui.calendar.LinkState
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Replacing a calendar's address asks first, and a failed replace can be tried again. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AddressDialogsTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val replace = context.getString(R.string.calendars_link_replace)
    private val question = context.getString(R.string.calendars_link_replace_title)

    private var link by mutableStateOf(LinkState(calendar = "c1", exists = true))
    private var replaced = 0

    private fun show() {
        rule.setContent {
            AddressDialogs(
                calendar = Calendar(id = "c1", name = "Work"),
                link = link,
                onReplace = { replaced++ },
                onClose = {},
            )
        }
        rule.waitForIdle()
    }

    private fun shown(text: String) = rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `Replace asks before it breaks the address`() {
        show()
        rule.onNodeWithText(replace).performClick()
        rule.waitForIdle()
        assertEquals(0, replaced)
        assertEquals(true, shown(question))
        rule.onNodeWithText(replace).performClick()
        rule.waitForIdle()
        assertEquals(1, replaced)
    }

    @Test
    fun `a failed replace keeps the question to try again`() {
        show()
        rule.onNodeWithText(replace).performClick()
        rule.onNodeWithText(replace).performClick()
        link = link.copy(busy = true)
        rule.waitForIdle()
        link = link.copy(busy = false)
        rule.waitForIdle()
        assertEquals(true, shown(question))
        rule.onNodeWithText(replace).performClick()
        rule.waitForIdle()
        assertEquals(2, replaced)
    }

    @Test
    fun `an address already issued says so and offers Replace and Close, not Revoke`() {
        show()
        assertEquals(true, shown(context.getString(R.string.calendars_link_exists)))
        assertEquals(true, shown(replace))
        assertEquals(true, shown(context.getString(MochiR.string.common_close)))
        assertEquals(false, shown(context.getString(R.string.calendars_link_revoke)))
    }

    @Test
    fun `a new address offers Copy and Close, and Copy puts it on the clipboard`() {
        val url = "https://example.org/calendars/abc/calendar.ics?token=t"
        link = LinkState(calendar = "c1", url = url, exists = true)
        show()
        assertEquals(true, shown(context.getString(R.string.calendars_link_once)))
        assertEquals(true, shown(context.getString(MochiR.string.common_close)))
        assertEquals(false, shown(replace))
        assertEquals(false, shown(context.getString(R.string.calendars_link_revoke)))
        rule.onNodeWithText(context.getString(MochiR.string.common_copy)).performClick()
        rule.waitForIdle()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(url, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        assertEquals(true, shown(context.getString(R.string.calendars_link_title)))
    }

    @Test
    fun `the new address shows once it is minted`() {
        show()
        rule.onNodeWithText(replace).performClick()
        rule.onNodeWithText(replace).performClick()
        link = link.copy(url = "https://example.org/calendars/abc/calendar.ics?token=t")
        rule.waitForIdle()
        assertEquals(false, shown(question))
        assertEquals(true, shown(context.getString(R.string.calendars_link_title)))
    }
}
