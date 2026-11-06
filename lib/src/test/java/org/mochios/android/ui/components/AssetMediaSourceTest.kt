// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.AssetHttpModule
import org.mochios.android.api.OWNED
import org.mochios.android.api.assetHeaders
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A video's frame and a player's stream go through the asset client, which
 * sends the session only with one of the app's own addresses and only to it:
 * the frame reader's own HTTP stack carried the headers across any redirect,
 * and a player marked every request a media file led it to.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AssetMediaSourceTest {

    private lateinit var server: MockWebServer
    private val clip = ByteArray(600_000) { index -> (index % 251).toByte() }
    private val seen = ConcurrentLinkedQueue<RecordedRequest>()
    private var ranges = true

    private val jar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            listOf(Cookie.Builder().domain(url.host).path("/").name("session").value("s3cret").build())
    }

    private fun origin() = server.url("/").toString().trimEnd('/')

    private fun client() = AssetHttpModule.assetClient(server = ::origin, token = { app -> "token-$app" }, jar = jar)

    @Before
    fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen.add(request)
                val range = request.getHeader("Range")
                if (!ranges || range == null) return MockResponse().setBody(Buffer().write(clip))
                val (from, to) = range.removePrefix("bytes=").split('-').map(String::toLong)
                if (from >= clip.size) return MockResponse().setResponseCode(416)
                val end = minOf(to, clip.size - 1L)
                return MockResponse().setResponseCode(206)
                    .setHeader("Content-Range", "bytes $from-$end/${clip.size}")
                    .setBody(Buffer().write(clip, from.toInt(), (end - from + 1).toInt()))
            }
        }
        server.start()
    }

    @After
    fun stop() = server.shutdown()

    private fun read(source: AssetMediaSource, position: Long, size: Int): ByteArray? {
        val buffer = ByteArray(size)
        val count = source.readAt(position, buffer, 0, size)
        return if (count < 0) null else buffer.copyOf(count)
    }

    @Test
    fun `an own clip is read in ranges with the session and its app's token`() {
        val address = "/feeds/1abc/-/attachments/9"
        val source = AssetMediaSource(client(), origin() + address, assetHeaders(address))
        assertEquals(clip.size.toLong(), source.size)
        val across = read(source, 262_100, 200)!!
        assertArrayEquals("a read stops at its block's end", clip.copyOfRange(262_100, 262_144), across)
        assertArrayEquals(clip.copyOfRange(262_144, 262_344), read(source, 262_144, 200))
        assertNull("past the end", read(source, clip.size.toLong(), 10))
        assertTrue(seen.all { it.getHeader("Authorization") == "Bearer token-feeds" && it.getHeader("Cookie") == "session=s3cret" })
        assertTrue("the mark never leaves the phone", seen.all { it.getHeader(OWNED) == null })
        assertEquals(listOf("bytes=0-262143", "bytes=262144-524287", "bytes=524288-786431"), seen.map { it.getHeader("Range") })
    }

    @Test
    fun `a server that ignores the range is read up to the block wanted`() {
        ranges = false
        val address = "/feeds/1abc/-/attachments/9"
        val source = AssetMediaSource(client(), origin() + address, assetHeaders(address))
        assertArrayEquals(clip.copyOfRange(300_000, 300_100), read(source, 300_000, 100))
        assertEquals(clip.size.toLong(), source.size)
    }

    @Test
    fun `an absolute clip on the user's server is read without either`() {
        val source = AssetMediaSource(client(), origin() + "/settings/-/x", assetHeaders(origin() + "/settings/-/x"))
        read(source, 0, 10)
        assertNull("THE POINT: no token", seen.single().getHeader("Authorization"))
        assertNull("THE POINT: and no session", seen.single().getHeader("Cookie"))
    }

    @Test
    fun `only a clip the picker handed over is read from the device`() {
        assertTrue(picked("content://media/picker/0/video/12"))
        assertFalse("THE POINT: a file address is never one", picked("file:///data/data/org.mochios.mochi/shared_prefs/session.xml"))
        assertFalse(picked("/feeds/1abc/-/attachments/9"))
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @Test
    fun `a player marks the request for its own address and no other`() {
        val own = "https://mochi.example/feeds/1abc/-/attachments/9"
        assertEquals("1", marked(DataSpec(Uri.parse(own)), own).httpRequestHeaders[OWNED])
        assertNull(
            "THE POINT: an address the media file named goes unmarked",
            marked(DataSpec(Uri.parse("https://mochi.example/settings/-/x")), own).httpRequestHeaders[OWNED],
        )
        assertNull("a player for another URL marks nothing", marked(DataSpec(Uri.parse(own)), null).httpRequestHeaders[OWNED])
    }
}
