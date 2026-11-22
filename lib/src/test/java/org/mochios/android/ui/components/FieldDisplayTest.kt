// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mochios.android.i18n.Clock
import org.mochios.android.i18n.DateFormat
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.NumberFormat
import org.mochios.android.i18n.UserPreferences
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * A field's value on a card or a row reads as the web's board card shows it,
 * by the field's type: never a checklist's JSON, a box's 1 or 0, an ISO day, an
 * unformatted number, an option id or a person's id.
 */
class FieldDisplayTest {

    private object English : Clock {
        override fun pattern(skeleton: String): String = skeleton

        override fun write(pattern: String, millis: Long, zone: TimeZone): String {
            val format = SimpleDateFormat(pattern, Locale.ENGLISH)
            format.timeZone = zone
            return format.format(Date(millis))
        }

        override fun span(skeleton: String, from: Long, to: Long, zone: TimeZone): String = ""
    }

    private val format = Format(
        UserPreferences(
            dateFormat = DateFormat.DD_SLASH_MM_YYYY,
            numberFormat = NumberFormat.EUROPEAN_DOT_COMMA,
            timezone = "America/Los_Angeles",
        ),
        clock = English,
    )

    private val today = LocalDate.of(2026, 11, 17)

    private val ada = "12C83B6Phg9Ap8bK58uqrzWKBYRXAo9pSzsHzSJnEsAnCnRpdob"

    private fun shown(
        type: String,
        value: String,
        choice: (String) -> FieldChoice? = { null },
        person: (String) -> String? = { null },
    ) = fieldDisplay(type, value, format, name = "Billable", choice = choice, person = person, today = today)

    @Test
    fun `a checklist shows its progress, not its JSON`() {
        val value = """[{"text":"Call","done":true},{"text":"Write","checked":false},{"text":"Send"}]"""
        assertEquals(FieldDisplay.Checklist(1, 3, "1/3"), shown("checklist", value))
    }

    @Test
    fun `a checklist is complete when every item is done`() {
        val value = """[{"text":"Call","done":true},{"text":"Write","checked":true}]"""
        assertEquals(true, (shown("checklist", value) as? FieldDisplay.Checklist)?.complete)
        assertEquals(false, (shown("checklist", """[{"text":"Call"}]""") as? FieldDisplay.Checklist)?.complete)
    }

    @Test
    fun `an empty or unreadable checklist shows nothing`() {
        assertNull(shown("checklist", "[]"))
        assertNull(shown("checklist", "not a list"))
    }

    @Test
    fun `a ticked box shows the field's name`() {
        assertEquals(FieldDisplay.Checkbox("Billable"), shown("checkbox", "1"))
        assertEquals(FieldDisplay.Checkbox("Billable"), shown("checkbox", "true"))
    }

    @Test
    fun `an unticked box shows nothing`() {
        assertNull(shown("checkbox", "0"))
        assertNull(shown("checkbox", "false"))
    }

    @Test
    fun `a date shows its calendar day in the user's format`() {
        // Los Angeles: at UTC midnight it is still the 16th there.
        assertEquals("18/11/2026", shown("date", "2026-11-18")?.text)
    }

    @Test
    fun `a date is overdue before today, not on it`() {
        assertEquals(FieldDisplay.Date("16/11/2026", overdue = true), shown("date", "2026-11-16"))
        assertEquals(FieldDisplay.Date("17/11/2026", overdue = false), shown("date", "2026-11-17"))
        assertEquals(FieldDisplay.Date("01/01/1960", overdue = true), shown("date", "1960-01-01"))
    }

    @Test
    fun `a date that is no date shows as stored`() {
        assertEquals(FieldDisplay.Text("soon"), shown("date", "soon"))
    }

    @Test
    fun `a number is in the user's number format with its own decimals`() {
        assertEquals(FieldDisplay.Number("1.234.567,50"), shown("number", "1234567.50"))
        assertEquals(FieldDisplay.Number("-0,5"), shown("number", "-0.5"))
        assertEquals(FieldDisplay.Number("12"), shown("number", "12"))
    }

    @Test
    fun `a stored number the server would refuse shows as stored`() {
        assertEquals(FieldDisplay.Text("1e3"), shown("number", "1e3"))
    }

    @Test
    fun `an option shows its name and colour`() {
        val options = mapOf("open" to FieldChoice("Open", "#22c55e"))
        assertEquals(FieldDisplay.Choice("Open", "#22c55e"), shown("enumerated", "open", choice = { id -> options[id] }))
    }

    @Test
    fun `an option the field does not have shows nothing, never its id`() {
        assertNull(shown("enumerated", "gone", choice = { null }))
    }

    @Test
    fun `a person shows their name`() {
        assertEquals(FieldDisplay.Person(ada, "Ada"), shown("user", ada, person = { id -> if (id == ada) "Ada" else null }))
    }

    @Test
    fun `a person with no known name shows their fingerprint, never their id`() {
        assertEquals(FieldDisplay.Person(ada, "6MF-gfL-93a"), shown("user", ada))
        assertEquals(FieldDisplay.Person(ada, "6MF-gfL-93a"), shown("user", ada, person = { " " }))
    }

    @Test
    fun `text shows as stored and a blank value shows nothing`() {
        assertEquals(FieldDisplay.Text("Call back"), shown("text", "Call back"))
        assertNull(shown("text", " "))
        assertNull(shown("number", ""))
    }
}
