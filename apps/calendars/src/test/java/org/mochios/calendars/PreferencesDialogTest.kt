// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.TimeFormat
import org.mochios.android.i18n.UserPreferences
import org.mochios.calendars.model.Calendar
import org.mochios.calendars.model.Preferences
import org.mochios.calendars.ui.dialogs.PreferencesDialog
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.format.TextStyle
import org.mochios.android.R as MochiR

/**
 * The preferences dialog as the web's: hours named by the user's clock, work
 * days as toggles in the user's week order, lengths up to four hours, and
 * Save only once something changed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h1600dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreferencesDialogTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val calendars = listOf(Calendar(id = "c1", name = "Home", default = true))

    private fun show(preferences: Preferences, user: UserPreferences = UserPreferences()) {
        rule.setContent {
            CompositionLocalProvider(LocalFormat provides Format(user)) {
                PreferencesDialog(preferences = preferences, calendars = calendars, saving = false, onDismiss = {}, onConfirm = {})
            }
        }
        rule.waitForIdle()
    }

    private fun save() = rule.onNode(hasText(context.getString(MochiR.string.common_save)) and hasClickAction())

    @Test
    fun `working hours read in the user's clock`() {
        val user = UserPreferences(timeFormat = TimeFormat.H12)
        show(Preferences(), user)
        val format = Format(user)
        assertEquals(1, rule.onAllNodesWithText(format.formatHour(8)).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("08:00").fetchSemanticsNodes().size)
    }

    @Test
    fun `work days are toggles in the order the user's week runs`() {
        show(Preferences(), UserPreferences(weekStartsOn = 6))
        val locale = context.resources.configuration.locales[0]
        val names = listOf(6, 7, 1, 2, 3, 4, 5).map { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, locale) }
        val lefts = names.map { rule.onNodeWithText(it).fetchSemanticsNode().boundsInRoot }
        for (index in 1 until lefts.size) {
            assertTrue(names[index], lefts[index].top > lefts[index - 1].top || lefts[index].left > lefts[index - 1].left)
        }
    }

    @Test
    fun `a length set on the web in hours reads as hours`() {
        show(Preferences(duration = 180))
        rule.onNodeWithText(context.resources.getQuantityString(R.plurals.calendars_hours, 3, "3")).assertExists()
    }

    @Test
    fun `Save waits until something changes`() {
        show(Preferences(days = listOf(1, 2, 3, 4, 5)))
        save().assertIsNotEnabled()
        val locale = context.resources.configuration.locales[0]
        rule.onNodeWithText(DayOfWeek.SATURDAY.getDisplayName(TextStyle.SHORT, locale)).performClick()
        rule.waitForIdle()
        save().assertIsEnabled()
    }
}
