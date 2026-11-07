// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.fetch.Fetcher
import coil3.key.Keyer
import coil3.network.DeDupeConcurrentRequestStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.Options
import coil3.toUri
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.mochios.android.auth.SessionManager

/**
 * Coil data for an image whose address someone else wrote, such as a markdown
 * image in a post, comment or wiki page. It is fetched without the session
 * cookie or app token, so the address cannot make the reader's phone act as
 * the reader on their own server.
 *
 * @property destination The address as written: absolute, or relative to the
 *   session server.
 */
data class AnonymousImage(val destination: String)

/**
 * [destination] as an absolute http(s) URL on or off the session server, or
 * null for any other scheme (`file:`, `content:`, `intent:`), which a peer's
 * body must not reach.
 */
internal fun resolveAnonymousImage(destination: String, serverUrl: String): String? {
    val server = serverUrl.toHttpUrlOrNull() ?: return null
    val resolved = server.resolve(destination) ?: return null
    return resolved.toString()
}

/**
 * Memory cache key for [AnonymousImage]: the resolved URL, so the same image
 * is decoded once however its address was written.
 */
class AnonymousImageKeyer(
    private val sessionManager: SessionManager,
) : Keyer<AnonymousImage> {

    override fun key(data: AnonymousImage, options: Options): String? =
        resolveAnonymousImage(data.destination, sessionManager.getServerUrlBlocking())
}

/**
 * Fetches [AnonymousImage] through [anonymousClient], with the same disk cache,
 * revalidation and concurrent request dedupe as the app's other images.
 */
@OptIn(ExperimentalCoilApi::class)
class AnonymousImageFetcherFactory(
    private val sessionManager: SessionManager,
    anonymousClient: () -> OkHttpClient,
) : Fetcher.Factory<AnonymousImage> {

    private val network = OkHttpNetworkFetcherFactory(
        callFactory = anonymousClient,
        cacheStrategy = { RevalidatingCacheStrategy() },
        concurrentRequestStrategy = { DeDupeConcurrentRequestStrategy() },
    )

    override fun create(
        data: AnonymousImage,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher? {
        val url = resolveAnonymousImage(data.destination, sessionManager.getServerUrlBlocking())
            ?: return null
        return network.create(url.toUri(), options, imageLoader)
    }
}
