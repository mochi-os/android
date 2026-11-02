// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import org.mochios.android.api.assetAuthHeaders
import org.mochios.android.auth.SessionManager

/**
 * Coil model routing a request to [VideoFrameFetcher]. Attachment URLs carry no
 * extension, so a distinct type is the only way the fetcher can claim them.
 * The url may be a server URL or a `content://` clip picked on the device.
 */
data class VideoFrame(val url: String)

/**
 * Decodes a video's opening frame with [MediaMetadataRetriever], which
 * range-requests only that frame rather than the whole clip as Coil's
 * `VideoFrameDecoder` would. Returning through Coil keeps the frame in the
 * memory cache, under the key [VideoFrameKeyer] gives it.
 *
 * @param share Runs the decode for a URL, or joins one already running for it.
 */
class VideoFrameFetcher(
    private val data: VideoFrame,
    private val sessionManager: SessionManager,
    private val context: Context,
    private val share: suspend (url: String, decode: () -> Bitmap?) -> Bitmap?,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val local = data.url.startsWith("content://") || data.url.startsWith("file://")
        val bitmap = share(data.url) { decode(local) } ?: return null
        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = false,
            dataSource = if (local) DataSource.DISK else DataSource.NETWORK,
        )
    }

    private fun decode(local: Boolean): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            // A clip picked on the device is read through the resolver that
            // handed it over; one on the server is a URL, and needs the asset
            // headers to get past auth.
            if (local) {
                retriever.setDataSource(context, data.url.toUri())
            } else {
                retriever.setDataSource(data.url, assetAuthHeaders(sessionManager, data.url))
            }
            retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    /**
     * Creates a [VideoFrameFetcher] for each [VideoFrame] request. Requests for
     * one URL that arrive while its frame is still decoding wait for that decode
     * instead of starting their own, so a video shown twice at once is read from
     * the server once. The decode runs in the factory's scope, so a waiting
     * request still gets the frame when the one that started it is cancelled.
     */
    class Factory(private val sessionManager: SessionManager) : Fetcher.Factory<VideoFrame> {

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private val inFlight = mutableMapOf<String, Deferred<Bitmap?>>()

        private val lock = Any()

        override fun create(
            data: VideoFrame,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = VideoFrameFetcher(data, sessionManager, options.context, ::share)

        private suspend fun share(url: String, decode: () -> Bitmap?): Bitmap? {
            val job = synchronized(lock) {
                inFlight.getOrPut(url) {
                    scope.async {
                        try {
                            decode()
                        } finally {
                            synchronized(lock) {
                                inFlight.remove(url)
                            }
                        }
                    }
                }
            }
            return job.await()
        }
    }
}

/**
 * Memory cache key for a [VideoFrame]: its URL. Coil caches nothing for a model
 * type without a keyer, so a thumbnail scrolled back into view fetched its
 * frame from the server again.
 */
class VideoFrameKeyer : Keyer<VideoFrame> {
    override fun key(data: VideoFrame, options: Options): String = data.url
}
