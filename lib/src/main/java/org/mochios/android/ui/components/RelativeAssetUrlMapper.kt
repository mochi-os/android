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
import org.mochios.android.api.OwnedAddress
import org.mochios.android.api.owned
import org.mochios.android.auth.SessionManager

/**
 * Coil [Mapper] expanding a server-relative address held as a plain `String`
 * into an absolute URL on the session server. It is fetched without a
 * credential, as every `String` is; resolved here, it cannot be read as a
 * path on the device instead. Anything else returns null, meaning "not mine -
 * pass through unchanged".
 */
class RelativeAssetUrlMapper(
    private val sessionManager: SessionManager,
) : Mapper<String, String> {

    override fun map(data: String, options: Options): String? {
        if (!owned(data)) return null
        val base = sessionManager.server.value.trimEnd('/')
        return if (base.isBlank()) null else "$base$data"
    }
}

/** Coil [Mapper] expanding one of the app's own addresses into an absolute URL on the session server. */
class OwnedAddressMapper(
    private val sessionManager: SessionManager,
) : Mapper<OwnedAddress, String> {

    override fun map(data: OwnedAddress, options: Options): String? {
        val base = sessionManager.server.value.trimEnd('/')
        return if (base.isBlank()) null else base + data.address
    }
}

/**
 * Marks an image request for one of the app's own addresses, an
 * [OwnedAddress], with [OWNED] before [OwnedAddressMapper] makes it absolute,
 * so the asset client sends it a credential. Any other request goes without,
 * a server-relative `String` included: only the type says an address is the
 * app's own. The two are cached apart, so an absolute URL naming the same
 * file is never answered with what a credential fetched, nor an own address
 * with what an anonymous request was given. The disk cache is cleared on
 * sign-out, so the server-relative key cannot answer for another server.
 */
class OwnedInterceptor : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data as? OwnedAddress ?: return chain.proceed()
        val headers = NetworkHeaders.Builder(chain.request.httpHeaders).set(OWNED, "1").build()
        val request = chain.request.newBuilder()
            .httpHeaders(headers)
            .memoryCacheKeyExtra(OWNED, "1")
            .diskCacheKey("$OWNED ${data.address}")
            .build()
        return chain.withRequest(request).proceed()
    }
}
