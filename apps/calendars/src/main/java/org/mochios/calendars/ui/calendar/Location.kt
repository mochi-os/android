// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.calendar

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.browser.customtabs.CustomTabsIntent
import org.mochios.android.i18n.UserPreferences
import org.mochios.android.util.flightLink
import org.mochios.android.util.flightNumber
import org.mochios.android.util.mapLink
import org.mochios.android.util.webUri
import java.net.URLEncoder

/**
 * Open an event's [location], from the event sheet or beside the editor's
 * field. A flight number opens on the user's flight tracker, in the tracker's
 * own app when one claims the link; anything else opens in the phone's map
 * app, or as a search on the user's map service when the phone has none.
 */
fun openLocation(context: Context, location: String, preferences: UserPreferences) {
    val flight = flightNumber(location)
    if (flight != null) {
        openWeb(context, flightLink(flight, preferences.flights), native = true)
        return
    }
    try {
        // launch-ok: a geo: URI the phone's own map app answers, not a web URL
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(geo(location))))
    } catch (_: ActivityNotFoundException) {
        openWeb(context, mapLink(location, preferences.maps), native = false)
    }
}

/**
 * Open [url] in a Custom Tab, first offering it to an app other than a
 * browser when [native]. That needs Android 11's way of asking for one;
 * before it the tab opens.
 */
private fun openWeb(context: Context, url: String, native: Boolean) {
    val uri = webUri(url) ?: return
    if (native && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val app = Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
        try {
            context.startActivity(app)
            return
        } catch (_: ActivityNotFoundException) {
            // No app claims it: the tab below.
        }
    }
    runCatching { CustomTabsIntent.Builder().build().launchUrl(context, uri) }
}

/**
 * A `geo:` URI searching for a free-text location, which the phone's own map
 * app answers; the encoder's `+` for a space becomes `%20`, which every map
 * app reads.
 */
fun geo(location: String): String =
    "geo:0,0?q=" + URLEncoder.encode(location.trim(), "UTF-8").replace("+", "%20")
