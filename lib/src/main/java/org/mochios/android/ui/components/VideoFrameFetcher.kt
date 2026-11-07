// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaDataSource
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import org.mochios.android.api.assetHeaders
import org.mochios.android.api.resolved
import org.mochios.android.auth.SessionManager
import java.io.EOFException
import java.io.IOException

/**
 * Coil model routing a request to [VideoFrameFetcher]. Attachment URLs carry no
 * extension, so a distinct type is the only way the fetcher can claim them.
 * The url may be a server URL or a `content://` clip picked on the device.
 */
data class VideoFrame(val url: String)

/**
 * Decodes a video's opening frame with [MediaMetadataRetriever], which reads
 * only the ranges that frame needs rather than the whole clip as Coil's
 * `VideoFrameDecoder` would. Returning through Coil keeps the frame in the
 * memory cache, under the key [VideoFrameKeyer] gives it.
 *
 * @param share Runs the decode for a URL, or joins one already running for it.
 */
class VideoFrameFetcher(
    private val data: VideoFrame,
    private val sessionManager: SessionManager,
    private val client: () -> OkHttpClient,
    private val context: Context,
    private val share: suspend (url: String, decode: () -> Bitmap?) -> Bitmap?,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val local = picked(data.url)
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
            // handed it over. Anything else goes through the asset client,
            // which sends the session only with one of the app's own addresses
            // and only to that address: the retriever's own HTTP stack would
            // carry the headers across any redirect the answer named.
            if (local) {
                retriever.setDataSource(context, data.url.toUri())
            } else {
                val url = resolved(sessionManager.getServerUrlBlocking(), data.url)
                retriever.setDataSource(AssetMediaSource(client(), url, assetHeaders(data.url)))
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
    class Factory(
        private val sessionManager: SessionManager,
        private val client: () -> OkHttpClient,
    ) : Fetcher.Factory<VideoFrame> {

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private val inFlight = mutableMapOf<String, Deferred<Bitmap?>>()

        private val lock = Any()

        override fun create(
            data: VideoFrame,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = VideoFrameFetcher(data, sessionManager, client, options.context, ::share)

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
 * Whether [address] is a clip picked on the device: a `content://` one, which
 * the picker handed over. A `file://` address is never one the app builds.
 */
internal fun picked(address: String): Boolean = address.startsWith("content://")

/**
 * The clip at [url] read through [client] in ranged blocks, with [headers] on
 * every request, for [MediaMetadataRetriever]. A server that ignores the range
 * answers with the whole clip, which is read up to the block wanted. A few
 * blocks are held, since the retriever reads a container's index in many small
 * pieces.
 */
internal class AssetMediaSource(
    private val client: OkHttpClient,
    private val url: String,
    private val headers: Map<String, String>,
) : MediaDataSource() {

    private val blocks = object : LinkedHashMap<Long, ByteArray>(HELD, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>): Boolean = size > HELD
    }

    // The clip's length: UNASKED before the first answer, -1 when the answer
    // did not say.
    private var length = UNASKED

    @Synchronized
    override fun getSize(): Long {
        if (length == UNASKED) block(0)
        return length
    }

    @Synchronized
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (size <= 0) return 0
        val start = position - position % BLOCK
        val block = block(start) ?: return -1
        val within = (position - start).toInt()
        if (within >= block.size) return -1
        val count = minOf(size, block.size - within)
        System.arraycopy(block, within, buffer, offset, count)
        return count
    }

    override fun close() {
        synchronized(this) { blocks.clear() }
    }

    /** The block starting at [start], or null past the end. */
    private fun block(start: Long): ByteArray? {
        blocks[start]?.let { return it }
        if (length >= 0 && start >= length) return null
        val request = Request.Builder().url(url).header("Range", "bytes=$start-${start + BLOCK - 1}")
        for ((name, value) in headers) request.header(name, value)
        client.newCall(request.build()).execute().use { response ->
            if (response.code == 416) {
                if (length == UNASKED) length = start
                return null
            }
            if (response.code != 200 && response.code != 206) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty response body")
            val source = body.source()
            if (response.code == 206) {
                length = response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: -1
            } else {
                length = body.contentLength().takeIf { it >= 0 } ?: -1
                try {
                    source.skip(start)
                } catch (_: EOFException) {
                    return null
                }
            }
            val read = Buffer()
            while (read.size < BLOCK) {
                if (source.read(read, BLOCK - read.size) == -1L) break
            }
            if (read.size == 0L) return null
            return read.readByteArray().also { bytes -> blocks[start] = bytes }
        }
    }

    private companion object {
        const val BLOCK = 256L * 1024
        const val HELD = 8
        const val UNASKED = -2L
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
