// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import android.content.Context
import android.content.Intent
import org.mochios.android.R
import org.mochios.android.i18n.AppContext

/**
 * Display names for the connected-service, network and push-transport keys the
 * server uses, as the web client names them. Brand and protocol names stay
 * verbatim in Latin script.
 */
private val PROVIDER_NAMES = mapOf(
    "apple" to "Apple",
    "bluesky" to "Bluesky",
    "claude" to "Claude",
    "facebook" to "Facebook",
    // A hosted account on the fediverse, named by the protocol it speaks.
    "fediverse" to "ActivityPub",
    "github" to "GitHub",
    "google" to "Google",
    "lemmy" to "Lemmy",
    "mastodon" to "Mastodon",
    "matrix" to "Matrix",
    "microsoft" to "Microsoft",
    "ntfy" to "ntfy",
    "openai" to "OpenAI",
    "pushbullet" to "Pushbullet",
    "stripe-customer" to "Stripe",
    "x" to "X",
    "xmpp" to "XMPP",
)

/** The keys named in words rather than by a brand, in the reader's language. */
private val PROVIDER_LABELS = mapOf(
    "browser" to R.string.provider_browser,
    "caldav" to R.string.provider_caldav,
    "email" to R.string.provider_email,
    "fcm" to R.string.provider_fcm,
    "mcp" to R.string.provider_mcp,
    "unifiedpush" to R.string.provider_unifiedpush,
    "url" to R.string.provider_url,
    "web" to R.string.provider_web,
)

/**
 * The name for [key], whatever its case. Every type the server sends is listed;
 * a key a later server adds is shown capitalised, a brand's likeliest spelling.
 */
fun providerName(key: String): String {
    val lower = key.lowercase()
    PROVIDER_NAMES[lower]?.let { return it }
    PROVIDER_LABELS[lower]?.let { return AppContext.get().getString(it) }
    return key.replaceFirstChar { it.uppercaseChar() }
}

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
 * Open an account a profile shows: its web page in the browser, or its XMPP
 * address in the chat app that handles one. Anything else is dropped, as is
 * a link no app can open.
 */
fun openProfileLink(context: Context, url: String) {
    val uri = profileUri(url) ?: return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri)) // launch-ok: profileUri admits only https and xmpp
    } catch (_: Exception) {
        // No app for it.
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
