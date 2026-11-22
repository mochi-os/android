// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mochios.android.format.formatFingerprint
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.model.Checklist
import org.mochios.android.util.fingerprint
import java.time.LocalDate

/** A number as the server stores one. */
private val NUMBER = Regex("""^-?\d+(\.\d+)?$""")

/**
 * An enumerated field's option, as a value names it.
 *
 * @property name The option's name.
 * @property colour Its `#RRGGBB` colour, "" for none.
 */
data class FieldChoice(val name: String, val colour: String = "")

/**
 * A field's value as a board card or a tree row shows it, decided by the
 * field's type ([fieldDisplay]). [text] is what it reads as.
 */
sealed class FieldDisplay {
    abstract val text: String

    /** Text, or a value no other case could read. */
    data class Text(override val text: String) : FieldDisplay()

    /** A number in the user's number format, with the decimals it was stored with. */
    data class Number(override val text: String) : FieldDisplay()

    /** An enumerated field's option. */
    data class Choice(override val text: String, val colour: String) : FieldDisplay()

    /** A person, by name, or by the fingerprint of their [id] when no name is known. */
    data class Person(val id: String, override val text: String) : FieldDisplay()

    /** A calendar day in the user's date format; [overdue] when it is before today. */
    data class Date(override val text: String, val overdue: Boolean) : FieldDisplay()

    /** A checklist's progress: [done] of [total] items, written "2/5". */
    data class Checklist(val done: Int, val total: Int, override val text: String) : FieldDisplay() {
        val complete: Boolean get() = done == total
    }

    /** A ticked checkbox, shown by the field's name. */
    data class Checkbox(override val text: String) : FieldDisplay()
}

/**
 * How a field's [value] shows on a card or a row, by its [type], as the web's
 * board card shows it: an option by its name and colour, a person by name
 * (else the fingerprint of their id, never the id), a date as the calendar
 * day in the user's format, a number in the user's number format with its own
 * decimals, a checklist as its progress and a ticked checkbox as the field's
 * [name]. Null when there is nothing to show: a blank value, an unticked box,
 * an empty checklist, or an option the field does not have.
 *
 * @param choice The option of the field an option id names, if any.
 * @param person The name the container knows for a person's id, if any.
 * @param today The day a date is overdue before; the user's today.
 */
fun fieldDisplay(
    type: String,
    value: String,
    format: Format,
    name: String = "",
    choice: (String) -> FieldChoice? = { null },
    person: (String) -> String? = { null },
    today: LocalDate = format.today(),
): FieldDisplay? {
    if (value.isBlank()) return null
    return when (type) {
        "enumerated" -> choice(value)?.let { option -> FieldDisplay.Choice(option.name, option.colour) }
        "user" -> FieldDisplay.Person(
            value,
            person(value)?.takeIf { known -> known.isNotBlank() } ?: formatFingerprint(fingerprint(value)),
        )
        "date" -> format.calendarDate(value)
            ?.let { day -> FieldDisplay.Date(format.formatDate(day), day.isBefore(today)) }
            ?: FieldDisplay.Text(value)
        "number" -> {
            val number = value.trim()
            if (NUMBER.matches(number)) {
                val decimals = number.substringAfter('.', "").length
                FieldDisplay.Number(format.formatNumber(number.toDouble(), decimals))
            } else {
                FieldDisplay.Text(value)
            }
        }
        "checkbox" -> if (value == "1" || value.equals("true", ignoreCase = true)) FieldDisplay.Checkbox(name) else null
        "checklist" -> {
            val items = Checklist.parse(value)
            if (items.isEmpty()) {
                null
            } else {
                val done = items.count { item -> item.done }
                FieldDisplay.Checklist(done, items.size, "${format.formatNumber(done)}/${format.formatNumber(items.size)}")
            }
        }
        else -> FieldDisplay.Text(value)
    }
}

/**
 * A field's value on a card ([compact]) or a row, by its type
 * ([fieldDisplay]); nothing when there is nothing to show.
 */
@Composable
fun FieldValue(
    type: String,
    value: String,
    modifier: Modifier = Modifier,
    name: String = "",
    compact: Boolean = false,
    choice: (String) -> FieldChoice? = { null },
    person: (String) -> String? = { null },
) {
    val display = fieldDisplay(type, value, LocalFormat.current, name, choice, person) ?: return
    FieldValue(display, modifier, compact)
}

/**
 * [display] drawn as a card's small line ([compact]) or a row's: an option
 * with its colour dot, a person with their avatar, a checklist's progress, a
 * ticked box's name, and on a row a date with a clock that turns red once
 * the date is overdue.
 */
@Composable
fun FieldValue(display: FieldDisplay, modifier: Modifier = Modifier, compact: Boolean = false) {
    val style = if (compact) {
        MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
    } else {
        MaterialTheme.typography.bodyMedium
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val icon = if (compact) 12.dp else 16.dp
    val gap = if (compact) 4.dp else 6.dp
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        when (display) {
            is FieldDisplay.Choice -> {
                val colour = parseHexColour(display.colour)
                if (colour != null) {
                    Box(
                        modifier = Modifier
                            .size(if (compact) 8.dp else 10.dp)
                            .background(colour, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(gap))
                }
                Line(display.text, style, muted)
            }

            is FieldDisplay.Person -> {
                EntityAvatar(
                    name = display.text,
                    src = personAvatarPath(display.id),
                    seed = display.id,
                    size = if (compact) 16.dp else 22.dp,
                )
                Spacer(modifier = Modifier.width(gap))
                Line(display.text, style, muted)
            }

            is FieldDisplay.Date -> {
                val tint = if (display.overdue && !compact) MaterialTheme.colorScheme.error else muted
                if (!compact) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(icon), tint = tint)
                    Spacer(modifier = Modifier.width(gap))
                }
                Line(display.text, style, tint)
            }

            is FieldDisplay.Checklist -> {
                val tint = if (display.complete) MaterialTheme.colorScheme.primary else muted
                Row(
                    modifier = Modifier
                        .background(tint.copy(alpha = 0.1f), MaterialTheme.shapes.small)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.CheckBox, contentDescription = null, modifier = Modifier.size(icon), tint = tint)
                    Spacer(modifier = Modifier.width(4.dp))
                    Line(display.text, style, tint)
                }
            }

            is FieldDisplay.Checkbox -> {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(icon), tint = muted)
                Spacer(modifier = Modifier.width(gap))
                Line(display.text, style, muted)
            }

            is FieldDisplay.Number, is FieldDisplay.Text -> Line(display.text, style, muted)
        }
    }
}

@Composable
private fun Line(text: String, style: TextStyle, colour: Color) {
    Text(text = text, style = style, color = colour, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
