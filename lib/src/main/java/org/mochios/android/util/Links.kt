// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import android.net.Uri

/**
 * Whether a peer-supplied URL is a web link — the only kind that may leave
 * the app.
 *
 * Wiki pages, feed posts, chat messages and attachment lists carry URLs
 * authored by remote peers. Launching one verbatim hands whatever scheme the
 * peer chose — `tel:`, `file:`, `intent:`, `javascript:`, another app's
 * custom scheme — to `ACTION_VIEW`, a Custom Tab, a WebView, or
 * DownloadManager with this app as the sender. The scheme is judged exactly
 * as [Uri.parse] would extract it: everything before the first colon.
 * (Pure string logic so it is testable on the JVM; [Uri] is stubbed there.)
 */
fun isWebUrl(url: String): Boolean {
    val colon = url.indexOf(':')
    if (colon < 0) return false
    val scheme = url.substring(0, colon)
    return scheme.equals("http", ignoreCase = true) ||
        scheme.equals("https", ignoreCase = true)
}

/**
 * Resolve a peer-supplied URL to a [Uri] when it is a web link, null
 * otherwise. Every site that launches or downloads a peer's URL resolves it
 * through here and drops anything else.
 */
fun webUri(url: String): Uri? = if (isWebUrl(url)) Uri.parse(url) else null
