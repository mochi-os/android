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
