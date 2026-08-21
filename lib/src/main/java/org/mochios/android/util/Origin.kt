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
internal fun isServerOrigin(url: HttpUrl, serverUrl: String): Boolean {
    val server = serverUrl.toHttpUrlOrNull() ?: return false
    return url.scheme == server.scheme &&
        url.host == server.host &&
        url.port == server.port
}

/**
 * Cache key for [url]'s origin - scheme, host and effective port, matching
 * [isServerOrigin]. A host-only key shares one bucket between http and https,
 * so a cookie set by one is replayed to the other.
 */
internal fun originOf(url: HttpUrl): String = "${url.scheme}://${url.host}:${url.port}"
