// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.i18n

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** A stored number: an optional minus, digits, and an optional fraction. */
private val EPOCH = Regex("""^-?\d+(\.\d+)?$""")

private const val DAY_MILLIS = 86_400_000L

/**
 * The calendar day a date field's [value] holds. The server stores an ISO day
 * (`2026-11-17`), which is that day wherever it is read. An older writer stored
 * epoch seconds (or milliseconds), and an import may hold an ISO date-time:
 * each is a moment, read as its date in [zone], the user's. Null when [value]
 * is blank or none of these.
 */
fun calendarDate(value: String, zone: ZoneId): LocalDate? {
    val text = value.trim()
    if (text.isEmpty()) return null
    runCatching { LocalDate.parse(text) }.getOrNull()?.let { day -> return day }
    val moment = when {
        EPOCH.matches(text) -> text.toDouble().toLong().let { epoch ->
            Instant.ofEpochMilli(if (kotlin.math.abs(epoch) < 1_000_000_000_000L) epoch * 1000L else epoch)
        }
        else -> runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(text) }.getOrNull()
    } ?: return null
    return moment.atZone(zone).toLocalDate()
}

/**
 * The milliseconds a Material date picker selects [date] as: midnight UTC of
 * that day, which the picker reads back as the same day in every zone.
 */
fun pickerMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** The day a Material date picker's selected [millis] stand for, before 1970 too. */
fun pickerDate(millis: Long): LocalDate = LocalDate.ofEpochDay(Math.floorDiv(millis, DAY_MILLIS))
