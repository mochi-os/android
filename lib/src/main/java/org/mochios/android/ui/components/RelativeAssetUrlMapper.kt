// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import coil3.intercept.Interceptor
import coil3.map.Mapper
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageResult
import coil3.request.Options
import org.mochios.android.api.OWNED
import org.mochios.android.api.owned
import org.mochios.android.auth.SessionManager

/**
 * Coil [Mapper] expanding a server-relative asset path
 * ("/people/<id>/-/avatar") into an absolute URL against the session server, so
 * call sites need no host. Anything else returns null, meaning "not mine - pass
 * through unchanged".
 */
class RelativeAssetUrlMapper(
    private val sessionManager: SessionManager,
) : Mapper<String, String> {

    override fun map(data: String, options: Options): String? {
        if (!owned(data)) return null
        val base = sessionManager.getServerUrlBlocking().trimEnd('/')
        return if (base.isBlank()) null else "$base$data"
    }
}

/**
 * Marks an image request for one of this app's own addresses ([owned]) with
 * [OWNED], before [RelativeAssetUrlMapper] makes it absolute, so the asset
 * client sends it the session and the app's token. Any other request goes
 * without: an absolute URL may be a peer's choice even on the user's server.
 * The two are cached apart, so an absolute URL naming the same file is never
 * answered with what the session fetched, nor an own address with what an
 * anonymous request was given. The disk cache is cleared on sign-out, so the
 * server-relative key cannot answer for another server.
 */
class OwnedInterceptor : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data
        if (data !is String || !owned(data)) return chain.proceed()
        val headers = NetworkHeaders.Builder(chain.request.httpHeaders).set(OWNED, "1").build()
        val request = chain.request.newBuilder()
            .httpHeaders(headers)
            .memoryCacheKeyExtra(OWNED, "1")
            .diskCacheKey("$OWNED $data")
            .build()
        return chain.withRequest(request).proceed()
    }
}
