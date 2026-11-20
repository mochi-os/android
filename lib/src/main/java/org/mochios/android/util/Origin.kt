// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * True when [url] is the origin named by [serverUrl]. Compares scheme, host and
 * effective port, never the host alone: a host match conflates http with https
 * and accepts a lookalike suffix. Fails closed if [serverUrl] will not parse.
 */
fun isServerOrigin(url: HttpUrl, serverUrl: String): Boolean {
    val server = serverUrl.toHttpUrlOrNull() ?: return false
    return url.scheme == server.scheme &&
        url.host == server.host &&
        url.port == server.port
}

/**
 * The origin of [address] as the app stores a server: `scheme://host`, with
 * the port only when it is not the scheme's own, and nothing after it. Null
 * when [address] does not parse or carries a user name or password. A path
 * kept with it would go into every address built from the stored one: a
 * pasted `https://example.org/login` would send the socket, the images and
 * push to `/login/...`.
 */
fun serverOrigin(address: String): String? {
    val url = address.trim().toHttpUrlOrNull() ?: return null
    if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
    return HttpUrl.Builder().scheme(url.scheme).host(url.host).port(url.port).build().toString().trimEnd('/')
}

/**
 * Cache key for [url]'s origin - scheme, host and effective port, matching
 * [isServerOrigin]. A host-only key shares one bucket between http and https,
 * so a cookie set by one is replayed to the other.
 */
internal fun originOf(url: HttpUrl): String = "${url.scheme}://${url.host}:${url.port}"
