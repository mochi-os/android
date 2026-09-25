// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import android.net.Uri
import org.mochios.android.i18n.Flights
import java.net.URLEncoder

/**
 * Whether a peer-supplied URL is a web link - the only kind that may leave the
 * app. Launching one verbatim would hand `tel:`, `file:`, `intent:` or
 * `javascript:` to ACTION_VIEW with this app as the sender. The scheme is
 * everything before the colon.
 */
fun isWebUrl(url: String): Boolean {
    val colon = url.indexOf(':')
    if (colon < 0) return false
    val scheme = url.substring(0, colon)
    return scheme.equals("http", ignoreCase = true) ||
        scheme.equals("https", ignoreCase = true)
}

/**
 * Resolve a peer-supplied URL to a [Uri] when it is a web link, null otherwise.
 * Every site that launches or downloads a peer's URL goes through here.
 */
fun webUri(url: String): Uri? = if (isWebUrl(url)) Uri.parse(url) else null

/** What a `mochi:/<entity>` path segment may contain. See [entityDeepLink]. */
private val ROUTE_SEGMENT = Regex("[A-Za-z0-9._-]{1,128}")

/**
 * Build the in-app route for a `mochi:/<entity>[/<sub>...]` intent, or null
 * when it cannot be trusted.
 *
 * The activity that receives these is exported, so [app] and every segment ride
 * in an intent any installed app can send. [app] must name one of [apps] - the
 * features this build hosts - and every segment must be a plain identifier, so
 * a sender cannot smuggle its own path or query into the route.
 */
fun entityDeepLink(app: String?, segments: List<String>, apps: List<String>): String? {
    if (app == null || app !in apps) return null
    val entity = segments.firstOrNull() ?: return null
    // `.` and `..` match the character class but are path traversal, not names.
    if (segments.any { !it.matches(ROUTE_SEGMENT) || it == "." || it == ".." }) return null
    return buildString {
        append('/').append(app).append('/').append(entity)
        for (s in segments.drop(1)) append('/').append(s)
    }
}

// A flight number on its own: an airline's two-character code (two letters, or
// a letter and a digit) and one to four digits with an optional letter, with or
// without a space between. matchEntire anchors both patterns.
private val BARE = Regex("([A-Z]{2}|[A-Z][0-9]|[0-9][A-Z]) ?([0-9]{1,4}[A-Z]?)")
// The airline's name and then its flight number, "Aer Lingus EI59": only the
// two-letter code form after a name, so "Gate B12" is not a flight.
private val NAMED = Regex(".*\\s([A-Z]{2}) ?([0-9]{1,4}[A-Z]?)")

/**
 * The flight number a location holds, or null. A location is a flight when it
 * is a flight number alone, or an airline's name followed by one, and nothing
 * else, so a street address, "EI59 gate 12" or "Alaska 1342" is not. Answers
 * the number normalised, upper case and unspaced. The web's flightNumber()
 * reads the same shape.
 */
fun flightNumber(location: String): String? {
    val text = location.trim().uppercase()
    val match = BARE.matchEntire(text) ?: NAMED.matchEntire(text) ?: return null
    return match.groupValues[1] + match.groupValues[2]
}

/** A flight's page on the user's tracker, by its number as [flightNumber] answers it. */
fun flightLink(flight: String, service: Flights): String = when (service) {
    Flights.FLIGHTAWARE -> "https://www.flightaware.com/live/flight/" + URLEncoder.encode(flight, "UTF-8")
    Flights.FLIGHTRADAR24 -> "https://www.flightradar24.com/data/flights/" + URLEncoder.encode(flight.lowercase(), "UTF-8")
}
