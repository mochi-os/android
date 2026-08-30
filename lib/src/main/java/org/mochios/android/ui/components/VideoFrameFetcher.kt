// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
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
 * memory cache.
 */
class VideoFrameFetcher(
    private val data: VideoFrame,
    private val sessionManager: SessionManager,
    private val context: Context,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val retriever = MediaMetadataRetriever()
        return try {
            // A clip picked on the device is read through the resolver that
            // handed it over; one on the server is a URL, and needs the asset
            // headers to get past auth.
            val local = data.url.startsWith("content://") || data.url.startsWith("file://")
            if (local) {
                retriever.setDataSource(context, data.url.toUri())
            } else {
                retriever.setDataSource(data.url, assetAuthHeaders(sessionManager, data.url))
            }
            val bitmap = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
            ImageFetchResult(
                image = bitmap.asImage(),
                isSampled = false,
                dataSource = if (local) DataSource.DISK else DataSource.NETWORK,
            )
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    class Factory(private val sessionManager: SessionManager) : Fetcher.Factory<VideoFrame> {
        override fun create(
            data: VideoFrame,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = VideoFrameFetcher(data, sessionManager, options.context)
    }
}
