// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

private val SEA = Regex("""^Etc/GMT([+-]\d{1,2})?$""")

/**
 * The city an IANA zone is named for, as a label beside a time in that zone:
 * "America/New_York" reads "New York", "UTC" stays "UTC". A sea zone is
 * named the zone database's way, where Etc/GMT-8 is eight hours ahead of
 * UTC, and reads as the offset it is: "UTC+8". Agrees with the web's
 * `zoneCity`, character for character.
 */
fun zoneCity(zone: String): String {
    val sea = SEA.matchEntire(zone)
    if (sea != null) {
        val offset = sea.groupValues[1]
        if (offset.isEmpty() || offset.substring(1).toInt() == 0) return "UTC"
        val sign = if (offset.startsWith("-")) "+" else "-"
        return "UTC" + sign + offset.substring(1)
    }
    return zone.substringAfterLast('/').replace('_', ' ')
}
