// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import coil3.annotation.ExperimentalCoilApi
import coil3.network.CacheStrategy
import coil3.network.NetworkHeaders
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.request.Options
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * How the image loader treats what it holds on disk. Coil's default serves a
 * held image without asking the server again, so an avatar behind an
 * unchanging address (`/people/<id>/-/avatar`) stayed as it was until evicted,
 * however often its owner changed it.
 *
 * This honours the server's `max-age`: within it the held copy is served; past
 * it, or under `no-cache`, the server is asked again with the copy's validator,
 * and a 304 keeps the held bytes. An answer that names no lifetime is served
 * from the cache as before - attachment and variant addresses never change
 * their bytes.
 */
@OptIn(ExperimentalCoilApi::class)
class RevalidatingCacheStrategy(
    private val now: () -> Long = System::currentTimeMillis,
) : CacheStrategy {

    override suspend fun read(
        cacheResponse: NetworkResponse,
        networkRequest: NetworkRequest,
        options: Options,
    ): CacheStrategy.ReadResult {
        // Offline, the loader asks for the held copy alone; there is no server
        // to ask again.
        if (control(networkRequest.headers).cached) return CacheStrategy.ReadResult(cacheResponse)
        val control = control(cacheResponse.headers)
        val lifetime = control.lifetime
        val fresh = when {
            control.revalidate -> false
            lifetime == null -> true
            else -> now() - cacheResponse.responseMillis < lifetime * 1000
        }
        if (fresh) return CacheStrategy.ReadResult(cacheResponse)
        val headers = networkRequest.headers.newBuilder()
        cacheResponse.headers["ETag"]?.let { headers.set("If-None-Match", it) }
        cacheResponse.headers["Last-Modified"]?.let { headers.set("If-Modified-Since", it) }
        return CacheStrategy.ReadResult(networkRequest.copy(headers = headers.build()))
    }

    override suspend fun write(
        cacheResponse: NetworkResponse?,
        networkRequest: NetworkRequest,
        networkResponse: NetworkResponse,
        options: Options,
    ): CacheStrategy.WriteResult {
        if (networkResponse.code == NOT_MODIFIED && cacheResponse != null) {
            // The held bytes stand: the held response, whose body reads them
            // from the cache, is written back with the new answer's headers
            // over its own and the time the answer came, from which the next
            // lifetime is counted. A response without a body would leave the
            // cache an empty image.
            val headers = cacheResponse.headers.newBuilder()
            for ((name, values) in networkResponse.headers.asMap()) headers.set(name, values)
            return CacheStrategy.WriteResult(
                cacheResponse.copy(
                    requestMillis = networkResponse.requestMillis,
                    responseMillis = networkResponse.responseMillis,
                    headers = headers.build(),
                )
            )
        }
        // The loader writes an answer to the cache before it looks at its
        // status: kept, a refusal or a failure would stand in for the image
        // until it was evicted. The held copy is written back as it was, still
        // stale, so the next load asks again; refusing the write instead would
        // leave the loader's open hold on the entry in place, and no later
        // answer could be stored over it.
        if (networkResponse.code !in 200..299) {
            return if (cacheResponse != null) CacheStrategy.WriteResult(cacheResponse) else CacheStrategy.WriteResult.DISABLED
        }
        return CacheStrategy.WriteResult(networkResponse)
    }

    /** A `Cache-Control`, as far as it bears on serving a held copy. */
    private class Control(val lifetime: Long?, val revalidate: Boolean, val cached: Boolean)

    private fun control(headers: NetworkHeaders): Control {
        var lifetime: Long? = null
        var revalidate = false
        var cached = false
        for (value in headers.getAll("Cache-Control")) {
            for (directive in value.split(',')) {
                val part = directive.trim().lowercase()
                when {
                    part == "no-cache" -> revalidate = true
                    part == "only-if-cached" -> cached = true
                    part.startsWith("max-age=") -> lifetime = part.removePrefix("max-age=").trim('"').toLongOrNull()
                }
            }
        }
        return Control(lifetime, revalidate, cached)
    }

    private companion object {
        const val NOT_MODIFIED = 304
    }
}

/**
 * Stale-if-error for the image loader's revalidations. Asked after a held copy,
 * a server that fails (a 5xx, or no answer at all) is answered as if it had
 * said the copy stands, so the held image is shown rather than a placeholder:
 * the loader fails a load on any answer but a 2xx or 304, though the held copy
 * survives on disk. A friend's server that is offline answers its avatar 502
 * through ours. The copy then keeps its lifetime from now, and is asked after
 * again when that passes. A 4xx passes through: the image is gone or no longer
 * the user's to see.
 */
class StaleIfError(private val now: () -> Long = System::currentTimeMillis) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header("If-None-Match") == null && request.header("If-Modified-Since") == null) {
            return chain.proceed(request)
        }
        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            // A load the caller gave up on is not a failure to cover.
            if (chain.call().isCanceled()) throw e
            return held(request)
        }
        if (response.code < 500) return response
        response.close()
        return held(request)
    }

    /** The answer a server gives when the copy asked after stands. */
    private fun held(request: Request): Response {
        val time = now()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(304)
            .message("Not Modified")
            .sentRequestAtMillis(time)
            .receivedResponseAtMillis(time)
            .build()
    }
}
