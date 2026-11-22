// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.graphics.Bitmap
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.decode.DecodeUtils
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import org.mochios.android.api.OWNED
import org.mochios.android.api.OwnedAddress
import org.mochios.android.api.assetHeaders
import org.mochios.android.api.resolved
import org.mochios.android.auth.SessionManager
import java.io.EOFException
import java.io.IOException
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Coil model routing a request to [VideoFrameFetcher]. Attachment URLs carry no
 * extension, so a distinct type is the only way the fetcher can claim them.
 * The url may be a server URL or a `content://` clip picked on the device;
 * [owned] when it was made from an [OwnedAddress], the only kind fetched with
 * a credential.
 */
class VideoFrame private constructor(val url: String, val owned: Boolean) {

    /** A clip at [url], fetched without a credential. */
    constructor(url: String) : this(url, false)

    override fun equals(other: Any?): Boolean = other is VideoFrame && other.url == url && other.owned == owned

    override fun hashCode(): Int = url.hashCode() * 31 + owned.hashCode()

    companion object {
        /** One of the app's own clips, at [address], fetched with a credential. */
        fun of(address: OwnedAddress): VideoFrame = VideoFrame(address.address, true)
    }
}

/**
 * Decodes a video's opening frame with [MediaMetadataRetriever], which reads
 * only the ranges that frame needs rather than the whole clip as Coil's
 * `VideoFrameDecoder` would. The frame is decoded at the size the request
 * asks for, not the video's own (a 4K frame is 33 MB, whatever the tile), and
 * returning through Coil keeps it in the memory cache under the key
 * [VideoFrameKeyer] gives it. A frame read over the network is also written to
 * Coil's disk cache, so a thumbnail outlives the process without the network.
 *
 * @param diskCache The image loader's disk cache, if it has one.
 * @param share Runs the decode for a key, or joins one already running for it.
 */
class VideoFrameFetcher(
    private val data: VideoFrame,
    private val options: Options,
    private val sessionManager: SessionManager,
    private val client: () -> OkHttpClient,
    private val diskCache: () -> DiskCache?,
    private val share: suspend (key: String, decode: () -> Frame?) -> Frame?,
) : Fetcher {

    /** A decoded frame, and whether it is smaller than the video's own. */
    class Frame(val bitmap: Bitmap, val sampled: Boolean)

    override suspend fun fetch(): FetchResult? {
        val local = picked(data.url)
        val bounds = bounds()
        // A frame at one size is not a frame at another, so the size is part
        // of both the decode a request joins and the disk entry it reads.
        val key = "${frameKey(data)} ${bounds.first}x${bounds.second} ${options.scale}"
        if (!local) cached(key)?.let { return it }
        val frame = share(key) { decode(local, bounds) } ?: return null
        if (!local) store(key, frame.bitmap)
        return ImageFetchResult(
            image = frame.bitmap.asImage(),
            isSampled = frame.sampled,
            dataSource = if (local) DataSource.DISK else DataSource.NETWORK,
        )
    }

    /** The size asked for in pixels; a dimension left undefined is the screen's width. */
    private fun bounds(): Pair<Int, Int> {
        val screen = options.context.resources.displayMetrics.widthPixels
        return options.size.width.pxOrElse { screen } to options.size.height.pxOrElse { screen }
    }

    /** The frame stored for [key], for Coil to decode at the size asked for. */
    private fun cached(key: String): FetchResult? {
        if (!options.diskCachePolicy.readEnabled) return null
        val cache = diskCache() ?: return null
        val snapshot = cache.openSnapshot(key) ?: return null
        return SourceFetchResult(
            source = ImageSource(file = snapshot.data, fileSystem = cache.fileSystem, diskCacheKey = key, closeable = snapshot),
            mimeType = FRAME_TYPE,
            dataSource = DataSource.DISK,
        )
    }

    private fun store(key: String, bitmap: Bitmap) {
        if (!options.diskCachePolicy.writeEnabled) return
        val cache = diskCache() ?: return
        val editor = cache.openEditor(key) ?: return
        try {
            cache.fileSystem.write(editor.data) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, FRAME_QUALITY, outputStream())
            }
            editor.commit()
        } catch (e: Exception) {
            // The frame is shown either way; only the copy for later is lost.
            editor.abort()
            if (e is CancellationException) throw e
        }
    }

    private fun decode(local: Boolean, bounds: Pair<Int, Int>): Frame? {
        val retriever = MediaMetadataRetriever()
        return try {
            // A clip picked on the device is read through the resolver that
            // handed it over. Anything else goes through the asset client,
            // which sends the session only with one of the app's own addresses
            // and only to that address: the retriever's own HTTP stack would
            // carry the headers across any redirect the answer named.
            if (local) {
                retriever.setDataSource(options.context, data.url.toUri())
            } else {
                val url = resolved(sessionManager.server.value, data.url)
                val own = if (data.owned) OwnedAddress.of(data.url) else null
                retriever.setDataSource(AssetMediaSource(client(), url, assetHeaders(own)))
            }
            frame(retriever, bounds)
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    /**
     * The opening frame scaled to [bounds]. From API 27 the retriever scales it
     * as it decodes, given the video's size; before that, or for a video that
     * does not state its size, the frame is decoded whole and scaled after.
     */
    private fun frame(retriever: MediaMetadataRetriever, bounds: Pair<Int, Int>): Frame? {
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && width != null && height != null && width > 0 && height > 0) {
            // The frame comes out turned upright, so it is measured that way.
            val turned = rotation % 180 != 0
            val shown = if (turned) height to width else width to height
            val multiplier = multiplier(shown, bounds)
            if (multiplier < 1.0) {
                // A square box of the scaled long side: the frame keeps its
                // shape inside it, so that side lands on the size asked for
                // whichever way the video is turned.
                val side = ceil(maxOf(shown.first, shown.second) * multiplier).toInt().coerceAtLeast(1)
                val frame = retriever.getScaledFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, side, side)
                    ?: return null
                return Frame(frame, sampled = true)
            }
        }
        val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
        return scaled(frame, multiplier(frame.width to frame.height, bounds))
    }

    /** How far a frame [source] in size is scaled to meet [bounds] as the request's scale fits it. */
    private fun multiplier(source: Pair<Int, Int>, bounds: Pair<Int, Int>): Double =
        DecodeUtils.computeSizeMultiplier(source.first, source.second, bounds.first, bounds.second, options.scale)

    /**
     * Creates a [VideoFrameFetcher] for each [VideoFrame] request. Requests for
     * one frame at one size that arrive while it is still decoding wait for
     * that decode instead of starting their own, so a video shown twice at once
     * is read from the server once. The decode runs in the factory's scope, so
     * a waiting request still gets the frame when the one that started it is
     * cancelled.
     */
    class Factory(
        private val sessionManager: SessionManager,
        private val client: () -> OkHttpClient,
    ) : Fetcher.Factory<VideoFrame> {

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private val inFlight = mutableMapOf<String, Deferred<Frame?>>()

        private val lock = Any()

        override fun create(
            data: VideoFrame,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = VideoFrameFetcher(data, options, sessionManager, client, { imageLoader.diskCache }, ::share)

        private suspend fun share(key: String, decode: () -> Frame?): Frame? {
            val job = synchronized(lock) {
                inFlight.getOrPut(key) {
                    scope.async {
                        try {
                            decode()
                        } finally {
                            synchronized(lock) {
                                inFlight.remove(key)
                            }
                        }
                    }
                }
            }
            return job.await()
        }
    }

    private companion object {
        const val FRAME_TYPE = "image/jpeg"
        const val FRAME_QUALITY = 90
    }
}

/**
 * [frame] scaled by [multiplier], which shrinks it or leaves it: a frame no
 * larger than the size asked for is kept as it is. The full-size frame is
 * recycled once the smaller one is made.
 */
internal fun scaled(frame: Bitmap, multiplier: Double): VideoFrameFetcher.Frame {
    if (multiplier >= 1.0) return VideoFrameFetcher.Frame(frame, sampled = false)
    val width = (frame.width * multiplier).roundToInt().coerceAtLeast(1)
    val height = (frame.height * multiplier).roundToInt().coerceAtLeast(1)
    val smaller = Bitmap.createScaledBitmap(frame, width, height, true)
    if (smaller !== frame) frame.recycle()
    return VideoFrameFetcher.Frame(smaller, sampled = true)
}

/** The key a [VideoFrame] is cached under: its URL, marked when it is one of the app's own. */
internal fun frameKey(data: VideoFrame): String = if (data.owned) "$OWNED ${data.url}" else data.url

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
 * frame from the server again. The fetcher reports a frame it scaled down as
 * sampled, so a cached frame too small for a larger tile is decoded again at
 * that tile's size.
 */
class VideoFrameKeyer : Keyer<VideoFrame> {
    override fun key(data: VideoFrame, options: Options): String = frameKey(data)
}
