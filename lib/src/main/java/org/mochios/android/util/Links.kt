// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import android.net.Uri

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
