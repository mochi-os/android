// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * True when [url] is the origin named by [serverUrl] — the user's own Mochi
 * server. Compares scheme, host and effective port ([HttpUrl.port] resolves the
 * default, so an implicit 443 matches an explicit one) rather than the host
 * alone: `http://host/`, `https://host:8443/` and `https://host/` are three
 * different origins, and a host comparison alone would also accept a lookalike
 * whose name merely ends with ours.
 *
 * Fails closed — an unparseable or empty [serverUrl] matches nothing, so
 * nothing is treated as ours on a host we cannot confirm.
 *
 * Three callers rely on this, each of which got the comparison wrong
 * independently before it was shared: the session cookie jar (which host may
 * receive the session), the API client's retarget (which origin a pinned
 * Retrofit request is sent to), and the push receiver (whether an endpoint is
 * local to the user's server).
 */
internal fun isServerOrigin(url: HttpUrl, serverUrl: String): Boolean {
    val server = serverUrl.toHttpUrlOrNull() ?: return false
    return url.scheme == server.scheme &&
        url.host == server.host &&
        url.port == server.port
}

/**
 * Cache key identifying [url]'s origin — the same scheme, host and effective
 * port [isServerOrigin] compares on, rendered as a string.
 *
 * Keyed by host alone, two origins sharing a hostname shared a bucket: a
 * `session` cookie set by `http://host` or `host:8443` was replayed to
 * `https://host`, and it also satisfied the caller's "already has a session"
 * test, suppressing the real stored session in favour of whatever that origin
 * had set.
 */
internal fun originOf(url: HttpUrl): String = "${url.scheme}://${url.host}:${url.port}"
