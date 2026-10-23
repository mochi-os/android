// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import android.content.Context
import android.content.Intent

/**
 * Display names for the connected-service and network keys the server uses,
 * as the web client names them. Brand names stay verbatim in Latin script; a
 * raw key is never shown.
 */
private val PROVIDER_NAMES = mapOf(
    "google" to "Google",
    "github" to "GitHub",
    "microsoft" to "Microsoft",
    "facebook" to "Facebook",
    "x" to "X",
    "stripe-customer" to "Stripe",
    "xmpp" to "XMPP",
)

fun providerName(key: String): String =
    PROVIDER_NAMES[key] ?: key.replaceFirstChar { it.uppercaseChar() }

/**
 * Open a peer-supplied web link in the browser. Anything but http and https is
 * dropped, as is a link no app can open.
 */
fun openWeb(context: Context, url: String) {
    val uri = webUri(url) ?: return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: Exception) {
        // No browser.
    }
}

/**
 * Follow a link a notification or another screen hands over: a path on this
 * server goes to [route], which opens it in the app; a web link off it - a
 * post or a person on another network, where a notification from a linked or
 * hosted account points - opens in the browser.
 */
fun followLink(context: Context, link: String, route: (String) -> Unit) {
    if (isWebUrl(link)) {
        openWeb(context, link)
    } else {
        route(link)
    }
}
