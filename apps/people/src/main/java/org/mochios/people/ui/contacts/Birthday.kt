// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import org.mochios.android.i18n.DateFormat
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.CompactTextField
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.people.R

/**
 * A birthday as the editor's fields hold it: the day and year as typed, the
 * month 1 to 12 or null. A year is optional; a day and month are not.
 */
data class BirthdayParts(val day: String = "", val month: Int? = null, val year: String = "") {
    val empty: Boolean
        get() = day.isBlank() && month == null && year.isBlank()
}

enum class BirthdayPart { DAY, MONTH, YEAR }

// The forms vCard writes a birthday in: 1985-04-12 or 19850412 with its
// year, --04-12 or --0412 without, each with an optional time after T.
private val FULL = Regex("""^(\d{4})-?(\d{2})-?(\d{2})(?:T.*)?$""")
private val YEARLESS = Regex("""^--(\d{2})-?(\d{2})(?:T.*)?$""")

/** The fields for a stored birthday, or null for one they cannot show, such as text. */
fun birthdayParts(value: String): BirthdayParts? {
    val text = value.trim()
    if (text.isEmpty()) return BirthdayParts()
    FULL.matchEntire(text)?.let { match ->
        val (year, month, day) = match.destructured
        return BirthdayParts(day.toInt().toString(), month.toInt(), year)
    }
    YEARLESS.matchEntire(text)?.let { match ->
        val (month, day) = match.destructured
        return BirthdayParts(day.toInt().toString(), month.toInt(), "")
    }
    return null
}

/**
 * The BDAY value for the fields: YYYY-MM-DD with a year, --MMDD without, empty
 * when every field is empty, and null when the fields do not make a day of
 * the year. Digits typed in any script count.
 */
fun birthdayValue(parts: BirthdayParts): String? {
    val day = ascii(parts.day.trim())
    val year = ascii(parts.year.trim())
    val month = parts.month
    if (day.isEmpty() && month == null && year.isEmpty()) return ""
    if (month == null || month !in 1..12 || !Regex("""\d{1,2}""").matches(day)) return null
    if (year.isNotEmpty() && !Regex("""\d{4}""").matches(year)) return null
    val d = day.toInt()
    if (d < 1 || d > length(month, year.toIntOrNull())) return null
    val mm = month.toString().padStart(2, '0')
    val dd = d.toString().padStart(2, '0')
    return if (year.isNotEmpty()) "$year-$mm-$dd" else "--$mm$dd"
}

/** The fields in the order the user's date format writes a date. */
fun birthdayOrder(format: DateFormat): List<BirthdayPart> = when (format) {
    DateFormat.YYYY_MM_DD -> listOf(BirthdayPart.YEAR, BirthdayPart.MONTH, BirthdayPart.DAY)
    DateFormat.MM_SLASH_DD_YYYY -> listOf(BirthdayPart.MONTH, BirthdayPart.DAY, BirthdayPart.YEAR)
    else -> listOf(BirthdayPart.DAY, BirthdayPart.MONTH, BirthdayPart.YEAR)
}

/** The days in a month; February has 29 when the year is unknown, since a birthday without its year may be the 29th. */
private fun length(month: Int, year: Int?): Int = when (month) {
    2 -> if (year == null || (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0))) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

private fun ascii(text: String): String =
    text.map { char -> char.digitToIntOrNull()?.digitToChar() ?: char }.joinToString("")

/** A month's name as it stands alone; a platform with no standalone names for the language answers a number, and then the plain form is used. */
private fun monthName(month: Int, locale: Locale): String {
    val standalone = Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, locale)
    return if (standalone.any { it.isLetter() }) standalone else Month.of(month).getDisplayName(TextStyle.FULL, locale)
}

/**
 * A birthday as a day, a month and an optional year, in the order the user's
 * date format writes them, so a birthday whose year is unknown can be entered
 * as one. [onValueChange] receives the value to store, or null with
 * `invalid` true while the fields do not make a day. A stored value the
 * fields cannot show, such as text, stands as written until it is cleared.
 */
@Composable
internal fun BirthdayField(value: String, onValueChange: (value: String?, invalid: Boolean) -> Unit) {
    var parts by remember { mutableStateOf(birthdayParts(value) ?: BirthdayParts()) }
    // The value these fields last reported: a different one arriving means the
    // contact changed underneath, and the fields follow it.
    var reported by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (value != reported) {
            reported = value
            parts = birthdayParts(value) ?: BirthdayParts()
        }
    }

    fun change(next: BirthdayParts) {
        parts = next
        val written = birthdayValue(next)
        if (written != null) reported = written
        onValueChange(written, written == null)
    }

    val clear = stringResource(R.string.people_contact_birthday_clear)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (value.isNotBlank() && birthdayParts(value) == null && parts.empty) {
            CompactTextField(value = value, onValueChange = {}, enabled = false, modifier = Modifier.width(192.dp))
            MochiIconButton(onClick = { onValueChange("", false) }) {
                Icon(Icons.Default.Close, contentDescription = clear)
            }
            return@Row
        }
        for (part in birthdayOrder(LocalFormat.current.preferences.dateFormat)) {
            when (part) {
                BirthdayPart.MONTH -> MonthField(parts.month) { change(parts.copy(month = it)) }
                BirthdayPart.DAY -> NumberField(
                    value = parts.day,
                    name = stringResource(R.string.people_contact_birthday_day),
                    length = 2,
                ) { change(parts.copy(day = it)) }
                BirthdayPart.YEAR -> NumberField(
                    value = parts.year,
                    name = stringResource(R.string.people_contact_birthday_year),
                    length = 4,
                ) { change(parts.copy(year = it)) }
            }
        }
        if (!parts.empty) {
            MochiIconButton(onClick = { change(BirthdayParts()) }) {
                Icon(Icons.Default.Close, contentDescription = clear)
            }
        }
    }
}

@Composable
private fun NumberField(value: String, name: String, length: Int, onValueChange: (String) -> Unit) {
    CompactTextField(
        value = value,
        onValueChange = { onValueChange(it.take(length)) },
        placeholder = name,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .width(if (length == 4) 80.dp else 60.dp)
            .semantics { contentDescription = name },
    )
}

@Composable
private fun MonthField(month: Int?, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val name = stringResource(R.string.people_contact_birthday_month)
    Box {
        MochiOutlinedButton(onClick = { expanded = true }, modifier = Modifier.semantics { contentDescription = name }) {
            Text(month?.let { monthName(it, locale) } ?: name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        MochiDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (option in 1..12) {
                MochiDropdownMenuItem(
                    text = { Text(monthName(option, locale)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    selected = option == month,
                )
            }
        }
    }
}
