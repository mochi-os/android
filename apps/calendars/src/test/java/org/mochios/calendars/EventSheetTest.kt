// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.UserPreferences
import org.mochios.calendars.model.Instance
import org.mochios.calendars.ui.calendar.EventSheet
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * The event sheet as the web's summary draws it: the dot in the event's own
 * colour, a cancelled title struck through, the status with its glyph, and
 * an all-day event's date with nothing before it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EventSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val day = LocalDate.of(2026, 9, 28)

    private fun show(instance: Instance) {
        rule.setContent { EventSheet(instance = instance, calendar = null, onDismiss = {}, onCopy = {}) }
        rule.waitForIdle()
    }

    private fun meeting(status: String = "") = Instance(
        event = "e1",
        calendar = "c1",
        summary = "Stand-up",
        colour = "#16a34a",
        status = status,
        start = day.atTime(10, 0).toEpochSecond(ZoneOffset.UTC),
        finish = day.atTime(11, 0).toEpochSecond(ZoneOffset.UTC),
    )

    private fun decoration(): TextDecoration? {
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("sheet-title", useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single().layoutInput.style.textDecoration
    }

    @Test
    fun `a cancelled event's title is struck through and its status carries the ban glyph`() {
        show(meeting("CANCELLED"))
        assertEquals(TextDecoration.LineThrough, decoration())
        assertEquals(1, rule.onAllNodesWithText(context.getString(R.string.calendars_status_cancelled)).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithTag("sheet-dashed", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `a tentative event keeps its title whole and its status carries a dashed ring`() {
        show(meeting("TENTATIVE"))
        assertNull(decoration())
        assertEquals(1, rule.onAllNodesWithTag("sheet-dashed", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `the dot is filled in the event's own colour`() {
        show(meeting())
        val pixels = rule.onNodeWithTag("sheet-dot", useUnmergedTree = true).captureToImage().toPixelMap()
        val centre = pixels[pixels.width / 2, pixels.height / 2]
        val green = Color(0xFF16A34A)
        assertTrue("$centre", abs(centre.red - green.red) < 0.02f && abs(centre.green - green.green) < 0.02f && abs(centre.blue - green.blue) < 0.02f)
    }

    @Test
    fun `an all-day event's date has nothing before it`() {
        val whole = Instance(
            event = "e2",
            calendar = "c1",
            summary = "Holiday",
            allday = true,
            date = day.toString(),
            start = day.atStartOfDay().toEpochSecond(ZoneOffset.UTC),
            finish = day.plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC),
        )
        show(whole)
        val format = Format(UserPreferences())
        val date = format.formatLongDate(day.atTime(12, 0).toEpochSecond(ZoneOffset.UTC), "UTC")
        assertEquals(1, rule.onAllNodesWithText(date).fetchSemanticsNodes().size)
    }
}
