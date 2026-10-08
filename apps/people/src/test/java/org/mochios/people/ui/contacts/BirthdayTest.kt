// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.contacts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.DateFormat
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.UserPreferences
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Reading and writing a birthday that may have no year. */
class BirthdayTest {

    @Test
    fun `reads every form vCard writes a birthday in`() {
        val full = BirthdayParts("12", 4, "1985")
        assertEquals(full, birthdayParts("1985-04-12"))
        assertEquals(full, birthdayParts("19850412"))
        assertEquals(full, birthdayParts("1985-04-12T00:00:00Z"))
        val yearless = BirthdayParts("12", 4, "")
        assertEquals(yearless, birthdayParts("--0412"))
        assertEquals(yearless, birthdayParts("--04-12"))
    }

    @Test
    fun `reads nothing as empty fields and text as nothing it can show`() {
        assertEquals(BirthdayParts(), birthdayParts(""))
        assertNull(birthdayParts("circa 1800"))
    }

    @Test
    fun `writes a full date with its year and --MMDD without one`() {
        assertEquals("1985-04-12", birthdayValue(BirthdayParts("12", 4, "1985")))
        assertEquals("--1002", birthdayValue(BirthdayParts("2", 10, "")))
        assertEquals("--1002", birthdayValue(BirthdayParts(" 2 ", 10, " ")))
        assertEquals("", birthdayValue(BirthdayParts()))
    }

    @Test
    fun `refuses fields that do not make a day of the year`() {
        for (parts in listOf(
            BirthdayParts("12", null, ""),
            BirthdayParts("", 4, ""),
            BirthdayParts("", null, "1985"),
            BirthdayParts("31", 4, ""),
            BirthdayParts("0", 4, ""),
            BirthdayParts("x", 4, ""),
            BirthdayParts("12", 4, "85"),
            BirthdayParts("29", 2, "2023"),
            BirthdayParts("29", 2, "1900"),
        )) {
            assertNull(parts.toString(), birthdayValue(parts))
        }
    }

    @Test
    fun `takes the 29th of February without a year or in a leap year`() {
        assertEquals("--0229", birthdayValue(BirthdayParts("29", 2, "")))
        assertEquals("2024-02-29", birthdayValue(BirthdayParts("29", 2, "2024")))
        assertEquals("2000-02-29", birthdayValue(BirthdayParts("29", 2, "2000")))
    }

    @Test
    fun `reads digits typed in another script`() {
        assertEquals("1985-04-12", birthdayValue(BirthdayParts("١٢", 4, "١٩٨٥")))
        assertEquals("--0412", birthdayValue(BirthdayParts("१२", 4, "")))
    }

    @Test
    fun `round-trips what it reads`() {
        for (value in listOf("1985-04-12", "--0412", "2024-02-29", "--1231")) {
            assertEquals(value, birthdayValue(birthdayParts(value)!!))
        }
    }

    @Test
    fun `follows the order the user writes a date in`() {
        assertEquals(listOf(BirthdayPart.YEAR, BirthdayPart.MONTH, BirthdayPart.DAY), birthdayOrder(DateFormat.YYYY_MM_DD))
        assertEquals(listOf(BirthdayPart.MONTH, BirthdayPart.DAY, BirthdayPart.YEAR), birthdayOrder(DateFormat.MM_SLASH_DD_YYYY))
        for (format in listOf(DateFormat.DD_SLASH_MM_YYYY, DateFormat.DD_DOT_MM_YYYY, DateFormat.D_MMM_YYYY)) {
            assertEquals(listOf(BirthdayPart.DAY, BirthdayPart.MONTH, BirthdayPart.YEAR), birthdayOrder(format))
        }
    }
}

/** The editor's birthday field: a day, a month and an optional year. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BirthdayFieldTest {

    @get:Rule
    val rule = createComposeRule()

    private var reported: Pair<String?, Boolean>? = null

    /** The contact's stored birthday, as the screen holds it. */
    private val stored = mutableStateOf("")

    private fun show(initial: String, format: DateFormat = DateFormat.DD_SLASH_MM_YYYY) {
        stored.value = initial
        rule.setContent {
            CompositionLocalProvider(LocalFormat provides Format(UserPreferences(dateFormat = format))) {
                BirthdayField(stored.value) { written, invalid ->
                    reported = written to invalid
                    if (written != null) stored.value = written
                }
            }
        }
    }

    private fun field(name: String): SemanticsNodeInteraction = rule.onNodeWithContentDescription(name)

    /** What a field holds as typed, without its placeholder. */
    private fun typed(name: String): String =
        field(name).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun pick(month: String) {
        field("Month").performClick()
        rule.onNodeWithText(month).performClick()
    }

    @Test
    fun `opens a birthday without its year with the year left empty`() {
        show("--04-12")
        assertEquals("12", typed("Day"))
        rule.onNodeWithText("April").assertExists()
        assertEquals("", typed("Year"))
    }

    @Test
    fun `reports a birthday entered without its year`() {
        show("")
        field("Day").performTextInput("2")
        pick("October")
        assertEquals("--1002" to false, reported)
    }

    @Test
    fun `reports fields that are not a day as invalid`() {
        show("")
        field("Day").performTextInput("31")
        pick("April")
        assertEquals(null to true, reported)
    }

    @Test
    fun `keeps the day as typed while the birthday it makes comes back`() {
        show("--04-12")
        field("Day").performTextReplacement("05")
        assertEquals("--0405" to false, reported)
        assertEquals("05", typed("Day"))
    }

    @Test
    fun `follows a birthday that changes underneath`() {
        show("1985-04-12")
        stored.value = "--10-02"
        rule.waitForIdle()
        assertEquals("2", typed("Day"))
        rule.onNodeWithText("October").assertExists()
        assertEquals("", typed("Year"))
    }

    @Test
    fun `removes a birthday with Clear`() {
        show("1985-04-12")
        field("Clear").performClick()
        assertEquals("" to false, reported)
        assertEquals("", typed("Day"))
    }

    @Test
    fun `shows a birthday it cannot read as written, until it is cleared`() {
        show("circa 1800")
        rule.onNodeWithText("circa 1800").assertExists()
        field("Day").assertDoesNotExist()
        field("Clear").performClick()
        assertEquals("" to false, reported)
        field("Day").assertExists()
    }

    @Test
    fun `orders the fields as the date format writes a date`() {
        show("1985-04-12", DateFormat.YYYY_MM_DD)
        val year = field("Year").getBoundsInRoot().left
        val month = field("Month").getBoundsInRoot().left
        val day = field("Day").getBoundsInRoot().left
        assertTrue("year $year, month $month, day $day", year < month && month < day)
    }
}

/** The editor carries what the birthday field reports into the contact's form. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BirthdayFormTest {

    @get:Rule
    val rule = createComposeRule()

    private var form = ContactForm(name = "Ada", birthday = "1985-04-12")

    @Test
    fun `holds Save while the birthday is not a day, and keeps the stored one`() {
        rule.setContent {
            var state by remember { mutableStateOf(form) }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ContactFields(form = state, books = emptyList(), onChange = {
                    state = it
                    form = it
                })
            }
        }
        val day = rule.onNodeWithContentDescription("Day").performScrollTo()
        day.performTextReplacement("31")
        assertTrue(form.birthdayInvalid)
        assertFalse(form.valid)
        assertEquals("1985-04-12", form.birthday)
        day.performTextReplacement("30")
        assertFalse(form.birthdayInvalid)
        assertTrue(form.valid)
        assertEquals("1985-04-30", form.birthday)
    }
}
