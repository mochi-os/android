// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.UserPreferences
import org.mochios.android.util.zoneCity
import org.mochios.calendars.model.Zone
import org.mochios.calendars.ui.editor.EventMoments
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Start and End rows always show the zone each end is typed in, and line up. */
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
    private val context = RuntimeEnvironment.getApplication()
    private val starts = context.getString(R.string.calendars_event_zone_start)
    private val ends = context.getString(R.string.calendars_event_zone_finish)

    /** The same day as an all-day form holds it: its UTC midnight, and the next. */
    private val day = 1_790_035_200L
    private val next = day + 86_400

    private var allday by mutableStateOf(false)
    private var ordered by mutableStateOf(true)
    private var finished by mutableStateOf<Long?>(null)

    private fun show(zone: Zone, allday: Boolean = false) {
        this@EventMomentsTest.allday = allday
        rule.setContent {
            Box(Modifier.width(352.dp)) {
                Column {
                    val whole = this@EventMomentsTest.allday
                    EventMoments(
                        start = if (whole) day else start,
                        finish = this@EventMomentsTest.finished ?: if (whole) next else finish,
                        allday = whole,
                        zone = zone,
                        own = "Europe/London",
                        ordered = this@EventMomentsTest.ordered,
                        onStart = {},
                        onFinish = {},
                        onZone = {},
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun bounds(text: String) =
        rule.onAllNodesWithText(text).fetchSemanticsNodes().map { it.boundsInRoot }

    private fun shown(description: String) =
        rule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `both ends show their zone even in the user's own, with no button to reveal them`() {
        show(Zone("Europe/London", "Europe/London"))
        assertTrue(shown(starts))
        assertTrue(shown(ends))
        assertEquals(2, rule.onAllNodesWithText(zoneCity("Europe/London")).fetchSemanticsNodes().size)
    }

    @Test
    fun `an end written with no zone shows the user's own`() {
        show(Zone("", ""))
        assertEquals(2, rule.onAllNodesWithText(zoneCity("Europe/London")).fetchSemanticsNodes().size)
    }

    @Test
    fun `each end names its own zone`() {
        show(Zone("Europe/London", "America/New_York"))
        assertEquals(1, rule.onAllNodesWithText(zoneCity("Europe/London")).fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodesWithText(zoneCity("America/New_York")).fetchSemanticsNodes().size)
    }

    @Test
    fun `an all-day event has no times and shows no zones`() {
        show(Zone("Europe/London", "Europe/London"), allday = true)
        assertEquals(false, shown(starts))
        assertEquals(false, shown(ends))
    }

    @Test
    fun `a one-day all-day event ends on the day it starts, not the day after`() {
        show(Zone("Europe/London", "Europe/London"), allday = true)
        // The form holds the day after its last; both fields show the one day.
        assertEquals(2, rule.onAllNodesWithText(format.formatDate(day, "UTC")).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText(format.formatDate(next, "UTC")).fetchSemanticsNodes().size)
    }

    @Test
    fun `an end before the start says so beneath End`() {
        val backwards = context.getString(R.string.calendars_event_backwards)
        show(Zone("UTC", "UTC"))
        assertEquals(0, rule.onAllNodesWithText(backwards).fetchSemanticsNodes().size)
        finished = start - 3_600
        ordered = false
        rule.waitForIdle()
        val said = rule.onAllNodesWithText(backwards).fetchSemanticsNodes().single().boundsInRoot
        val end = bounds(format.formatTime(start - 3_600, "UTC")).single()
        assertTrue("beneath End", said.top >= end.bottom)
    }

    @Test
    fun `turning all day on hides the times without widening the dates`() {
        show(Zone("UTC", "UTC"))
        val timed = bounds(format.formatDate(start, "UTC"))
        allday = true
        rule.waitForIdle()
        // All day reads the dates in UTC too, so they keep their text.
        val whole = bounds(format.formatDate(start, "UTC"))
        assertEquals(0, rule.onAllNodesWithText(format.formatTime(start, "UTC")).fetchSemanticsNodes().size)
        assertEquals(2, whole.size)
        for (index in 0..1) {
            assertEquals(timed[index].left, whole[index].left)
            assertEquals(timed[index].width, whole[index].width)
        }
    }

    @Test
    fun `the start and end dates and times line up`() {
        show(Zone("UTC", "UTC"))
        val dates = bounds(format.formatDate(start, "UTC"))
        assertEquals(2, dates.size)
        assertEquals(dates[0].left, dates[1].left)
        assertEquals(dates[0].width, dates[1].width)
        val begins = bounds(format.formatTime(start, "UTC")).single()
        val finishes = bounds(format.formatTime(finish, "UTC")).single()
        assertEquals(begins.left, finishes.left)
    }
}
