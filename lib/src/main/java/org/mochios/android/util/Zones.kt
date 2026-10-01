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

/**
 * One zone under one name, and the list a picker offers, as the web's time
 * zone picker and map name them: a zone's current name is the one the map
 * draws, so Asia/Calcutta reads as Asia/Kolkata and Europe/Kiev as
 * Europe/Kyiv whichever a calendar, a stored preference or the platform used.
 */
object Zones {
    /**
     * What the zone functions ask of the platform's zone database: its own
     * name for a zone among the zone's aliases, and the zones of places it
     * lists. Tests stand in for the platform, whose ICU is not there on the
     * JVM.
     */
    interface Registry {
        /** The platform's own name for [zone], or null for a zone it does not know. */
        fun canonical(zone: String): String?

        /** Every zone of a place the platform lists, each under one of its names. */
        fun places(): Collection<String>
    }

    /** The phone's own ICU, which resolves and lists zones as a browser does. */
    object Platform : Registry {
        override fun canonical(zone: String): String? =
            runCatching { android.icu.util.TimeZone.getCanonicalID(zone) }.getOrNull()?.takeIf { it.isNotBlank() }

        override fun places(): Collection<String> =
            android.icu.util.TimeZone.getAvailableIDs(
                android.icu.util.TimeZone.SystemTimeZoneType.CANONICAL_LOCATION,
                null,
                null,
            )
    }

    @Volatile
    private var cache: Pair<Registry, Map<String, String>>? = null

    /**
     * The drawn zone each other name stands for: a zone too small to draw
     * reads as the one it is shown within, and each name the platform gives
     * a drawn zone reads as that zone.
     */
    private fun renames(registry: Registry): Map<String, String> {
        cache?.let { if (it.first === registry) return it.second }
        val out = LinkedHashMap(ZoneNames.within)
        for (name in ZoneNames.drawn) {
            val listed = registry.canonical(name)
            if (listed != null && listed != name && listed !in out) out[listed] = name
        }
        cache = registry to out
        return out
    }

    /**
     * A zone's current name, the one the map draws it by. Another alias,
     * such as US/Eastern, reads as the drawn zone the platform resolves it
     * to. A zone the map does not draw, such as UTC or a sea zone, keeps its
     * name. Agrees with the web's `currentZone`.
     */
    fun current(zone: String, registry: Registry = Platform): String {
        if (zone in ZoneNames.drawn) return zone
        val renamed = renames(registry)
        renamed[zone]?.let { return it }
        val listed = registry.canonical(zone) ?: return zone
        if (listed in ZoneNames.drawn) return listed
        return renamed[listed] ?: zone
    }

    /** Whether [a] and [b] are one zone, under whichever of its names. */
    fun same(a: String, b: String, registry: Registry = Platform): Boolean =
        a == b || current(a, registry) == current(b, registry)

    /**
     * The platform's zones of places by their current names, in order, each
     * with the other names it goes by, which a search should also find.
     */
    fun listed(registry: Registry = Platform): Map<String, List<String>> {
        val out = sortedMapOf<String, MutableList<String>>(NaturalCompare)
        for (name in registry.places()) {
            val zone = current(name, registry)
            val others = out.getOrPut(zone) { mutableListOf() }
            if (name != zone && name !in others) others += name
        }
        return out
    }

    /**
     * The sea's zones, which the platform's list of places leaves out: one
     * per whole hour from UTC-12 to UTC+12, named the zone database's way,
     * where Etc/GMT+5 is UTC-5.
     */
    fun sea(): List<String> = (-12..12).map { offset ->
        when {
            offset == 0 -> "Etc/GMT"
            offset < 0 -> "Etc/GMT+${-offset}"
            else -> "Etc/GMT-$offset"
        }
    }

    /**
     * A zone's offset from UTC at [at], as the picker shows it beside the
     * zone: "UTC+5:30", "UTC-5", "UTC". Blank for a zone the platform does
     * not know.
     */
    fun offset(zone: String, at: java.time.Instant = java.time.Instant.now()): String {
        val id = runCatching { java.time.ZoneId.of(zone) }.getOrNull() ?: return ""
        val seconds = id.rules.getOffset(at).totalSeconds
        if (seconds == 0) return "UTC"
        val minutes = kotlin.math.abs(seconds) / 60
        val sign = if (seconds < 0) "-" else "+"
        val hours = "UTC$sign${minutes / 60}"
        return if (minutes % 60 == 0) hours else hours + ":" + (minutes % 60).toString().padStart(2, '0')
    }

    /** A zone as a picker names it: a sea zone by its offset, any other with spaces for underscores. */
    fun label(zone: String): String = if (SEA.matches(zone)) zoneCity(zone) else zone.replace('_', ' ')
}
