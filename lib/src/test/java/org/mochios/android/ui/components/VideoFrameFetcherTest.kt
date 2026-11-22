// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.Scale
import coil3.size.Size
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import org.robolectric.shadows.util.DataSource as Source

/**
 * A video's thumbnail frame is decoded at the size its tile asks for, not the
 * video's own (8 MB for 1080p, 33 MB for 4K, per tile), and kept on disk so it
 * outlives the process without going back to the network.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoFrameFetcherTest {
    private val context = RuntimeEnvironment.getApplication()
    private val clip = "content://media/external/video/media/7"

    @After
    fun end() = ShadowMediaMetadataRetriever.reset()

    private fun options(width: Int, height: Int, scale: Scale = Scale.FILL) =
        Options(context = context, size = Size(width, height), scale = scale)

    private fun fetcher(
        url: String,
        options: Options,
        disk: DiskCache? = null,
        share: suspend (String, () -> VideoFrameFetcher.Frame?) -> VideoFrameFetcher.Frame? = { _, decode -> decode() },
    ) = VideoFrameFetcher(VideoFrame(url), options, SessionManager(context), { OkHttpClient() }, { disk }, share)

    private fun video(width: Int, height: Int, rotation: Int = 0) {
        val source = Source.toDataSource(context, Uri.parse(clip))
        ShadowMediaMetadataRetriever.addMetadata(source, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH, "$width")
        ShadowMediaMetadataRetriever.addMetadata(source, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT, "$height")
        ShadowMediaMetadataRetriever.addMetadata(source, MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION, "$rotation")
    }

    private fun bitmap(width: Int, height: Int) = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    @Test
    fun `a 4K frame is decoded at the tile's size`() {
        video(3840, 2160)
        // A 400x300 tile filled by a 16:9 frame: the frame's height meets the
        // tile's, so its long side comes to 3840 * 300 / 2160.
        val small = bitmap(534, 300)
        ShadowMediaMetadataRetriever.addScaledFrame(Source.toDataSource(context, Uri.parse(clip)), 0L, 534, 534, small)
        // The retriever answers only the box it was given a frame for, so a
        // frame at all means the decode asked for this one.
        val result = runBlocking { fetcher(clip, options(400, 300)).fetch() } as ImageFetchResult
        assertEquals("THE POINT: the scaled decode, not the full frame", 534 to 300, size(result))
        assertTrue("reported as sampled, so a larger tile decodes again", result.isSampled)
    }

    @Test
    fun `a turned video is measured upright`() {
        video(1920, 1080, rotation = 90)
        // Shown 1080x1920: filling 400x300 takes its width to 400, its long side to 1920 * 400 / 1080.
        val small = bitmap(400, 712)
        ShadowMediaMetadataRetriever.addScaledFrame(Source.toDataSource(context, Uri.parse(clip)), 0L, 712, 712, small)
        val result = runBlocking { fetcher(clip, options(400, 300)).fetch() } as ImageFetchResult
        assertEquals(400 to 712, size(result))
    }

    @Test
    fun `a video smaller than its tile keeps its own frame`() {
        video(320, 180)
        val frame = bitmap(320, 180)
        ShadowMediaMetadataRetriever.addFrame(Source.toDataSource(context, Uri.parse(clip)), 0L, frame)
        val result = runBlocking { fetcher(clip, options(400, 300)).fetch() } as ImageFetchResult
        assertEquals(320 to 180, size(result))
        assertFalse(result.isSampled)
    }

    @Test
    fun `a video that does not state its size is scaled after decoding`() {
        val frame = bitmap(1920, 1080)
        ShadowMediaMetadataRetriever.addFrame(Source.toDataSource(context, Uri.parse(clip)), 0L, frame)
        val result = runBlocking { fetcher(clip, options(400, 300, Scale.FIT)).fetch() } as ImageFetchResult
        assertEquals("fitted to the tile", 400 to 225, size(result))
        assertTrue(result.isSampled)
    }

    @Test
    fun `a frame is scaled down, never up, and the full one let go`() {
        val frame = bitmap(200, 100)
        val same = scaled(frame, 1.5)
        assertSame(frame, same.bitmap)
        assertFalse(same.sampled)
        val full = bitmap(200, 100)
        val half = scaled(full, 0.5)
        assertEquals(100, half.bitmap.width)
        assertEquals(50, half.bitmap.height)
        assertTrue(half.sampled)
        assertTrue("the full-size frame is recycled", full.isRecycled)
    }

    private fun size(result: ImageFetchResult) = result.image.width to result.image.height

    @Test
    fun `a frame read over the network is kept on disk and read from there next time`() {
        val directory = java.io.File(context.cacheDir, "frames-" + System.nanoTime())
        val disk = DiskCache.Builder().directory(directory.toOkioPath()).build()
        val address = "/feeds/f1/-/attachments/v1"
        val keys = mutableListOf<String>()
        val decode: suspend (String, () -> VideoFrameFetcher.Frame?) -> VideoFrameFetcher.Frame? = { key, _ ->
            keys += key
            VideoFrameFetcher.Frame(bitmap(40, 30), sampled = true)
        }

        val first = runBlocking { fetcher(address, options(40, 30), disk, decode).fetch() }
        assertTrue(first is ImageFetchResult)
        assertEquals(1, keys.size)

        val second = runBlocking { fetcher(address, options(40, 30), disk, decode).fetch() }
        assertTrue("THE POINT: from disk, not the network", second is SourceFetchResult)
        assertEquals(DataSource.DISK, (second as SourceFetchResult).dataSource)
        second.source.close()
        assertEquals("not decoded again", 1, keys.size)

        runBlocking { fetcher(address, options(80, 60), disk, decode).fetch() }
        assertEquals("another size is another frame", 2, keys.size)
        assertNotEquals(keys[0], keys[1])
    }
}
