// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.util

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * A date field's value as epoch seconds. The server stores whatever the writer
 * sent: web writes an epoch, imports and the API accept an ISO date or
 * date-time, so all three are read.
 */
fun dateSeconds(value: String): Long? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    trimmed.toLongOrNull()?.let { return it }
    trimmed.toDoubleOrNull()?.let { return it.toLong() }
    runCatching { LocalDate.parse(trimmed).atStartOfDay(ZoneOffset.UTC).toEpochSecond() }.getOrNull()?.let { return it }
    runCatching { OffsetDateTime.parse(trimmed).toEpochSecond() }.getOrNull()?.let { return it }
    return runCatching { Instant.parse(trimmed).epochSecond }.getOrNull()
}
