// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.media.MediaMetadataRetriever
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
 * Coil model that routes a request to [VideoFrameFetcher]. Wrapping the URL in
 * a distinct type lets the fetcher claim only video-frame requests (chat /
 * feed attachment URLs are extension-less, so they can't be told apart from
 * images otherwise).
 */
data class VideoFrame(val url: String)

/**
 * Decodes a video's opening frame with [MediaMetadataRetriever], which
 * range-requests only that frame rather than downloading the whole clip (what
 * Coil's `VideoFrameDecoder` would do). Because it returns through Coil, the
 * frame is held in the memory cache — so re-scrolling or reopening a
 * conversation is instant instead of re-extracting every time.
 */
class VideoFrameFetcher(
    private val data: VideoFrame,
    private val sessionManager: SessionManager,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(data.url, assetAuthHeaders(sessionManager, data.url))
            val bitmap = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
            ImageFetchResult(
                image = bitmap.asImage(),
                isSampled = false,
                dataSource = DataSource.NETWORK,
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
        ): Fetcher = VideoFrameFetcher(data, sessionManager)
    }
}
