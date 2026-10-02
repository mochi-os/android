// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.UserPreferences
import org.mochios.calendars.model.Zone
import org.mochios.calendars.ui.editor.EventMoments
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Start and End rows line up, whether or not the End row carries the zone globe. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EventMomentsTest {

    @get:Rule
    val rule = createComposeRule()

    /** 2026-09-22 10:00 UTC, an hour long, so both ends fall on one day. */
    private val start = 1_790_071_200L
    private val finish = start + 3_600
    private val format = Format(UserPreferences())
    private val globe = RuntimeEnvironment.getApplication().getString(R.string.calendars_event_timezone)

    private fun show(revealed: Boolean) {
        rule.setContent {
            Box(Modifier.width(352.dp)) {
                Column {
                    EventMoments(
                        start = start,
                        finish = finish,
                        allday = false,
                        zone = Zone("UTC", "UTC"),
                        own = "UTC",
                        revealed = revealed,
                        enabled = true,
                        onStart = {},
                        onFinish = {},
                        onZone = {},
                        onReveal = {},
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun bounds(text: String) =
        rule.onAllNodesWithText(text).fetchSemanticsNodes().map { it.boundsInRoot }

    @Test
    fun `the start row keeps the globe's room, so its date and time line up with the end's`() {
        show(revealed = false)
        rule.onNodeWithContentDescription(globe).assertExists()
        val dates = bounds(format.formatDate(start, "UTC"))
        assertEquals(2, dates.size)
        assertEquals(dates[0].left, dates[1].left)
        assertEquals(dates[0].width, dates[1].width)
        val begins = bounds(format.formatTime(start, "UTC")).single()
        val ends = bounds(format.formatTime(finish, "UTC")).single()
        assertEquals(begins.left, ends.left)
        assertEquals(begins.width, ends.width)
    }

    @Test
    fun `with the zones shown there is no globe, and the rows still line up`() {
        show(revealed = true)
        rule.onNodeWithContentDescription(globe).assertDoesNotExist()
        val dates = bounds(format.formatDate(start, "UTC"))
        assertEquals(2, dates.size)
        assertEquals(dates[0].width, dates[1].width)
    }
}
