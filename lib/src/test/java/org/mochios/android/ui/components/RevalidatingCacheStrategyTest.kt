// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.fetch.SourceFetchResult
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.Options
import coil3.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockWebServer
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * The image loader's network fetch, with its disk cache, as the app builds it:
 * an avatar keeps its address when its owner changes it, so the held copy is
 * served only for the lifetime the server gave it and then asked after again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RevalidatingCacheStrategyTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var loader: ImageLoader
    private var offset = 0L
    private val answers = java.util.Collections.synchronizedList(mutableListOf<MockResponse>())
    private val seen = java.util.Collections.synchronizedList(mutableListOf<String>())

    @Before
    fun begin() {
        server = MockWebServer()
        // Answers in the order enqueued, and records every request, so one the
        // cache should have answered shows as a failure rather than a wait.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += "${request.path} ${request.getHeader("If-None-Match") ?: "-"}"
                return answers.removeFirstOrNull() ?: MockResponse().setResponseCode(599)
            }
        }
        server.start()
        val directory = File(context.cacheDir, "revalidate-test").also { it.deleteRecursively() }
        loader = ImageLoader.Builder(context)
            .diskCache { DiskCache.Builder().directory(directory.toOkioPath()).maxSizeBytes(1L shl 20).build() }
            .build()
    }

    @After
    fun end() {
        server.shutdown()
    }

    // Coil refuses a network fetch on the main thread, which the test's is.
    private fun fetch(path: String, online: Boolean = true): String = runBlocking(Dispatchers.IO) {
        val factory = OkHttpNetworkFetcherFactory(
            callFactory = { OkHttpClient() },
            cacheStrategy = { RevalidatingCacheStrategy { System.currentTimeMillis() + offset } },
        )
        // Offline the loader may not use the network, and asks for the held
        // copy alone.
        val options = if (online) Options(context) else Options(context, networkCachePolicy = CachePolicy.DISABLED)
        val fetcher = factory.create(server.url(path).toString().toUri(), options, loader)!!
        val result = fetcher.fetch() as SourceFetchResult
        // Closed as a decoder closes it: an open source holds its cache entry,
        // which the next answer could not then be written over.
        result.source.use { it.source().readUtf8() }
    }

    private fun answer(body: String, etag: String, control: String? = "private, max-age=300") =
        MockResponse().setBody(body).addHeader("ETag", etag).apply {
            if (control != null) addHeader("Cache-Control", control)
        }

    @Test
    fun `a held image is served within its lifetime and asked after once it passes`() {
        answers += answer("first", "\"a\"")
        assertEquals("first", fetch("/people/p/-/avatar"))
        assertEquals(listOf("/people/p/-/avatar -"), seen.toList())

        // Each answer is stamped with the real time it came; the offset is how
        // long after the last one each fetch happens.
        offset = 100_000
        assertEquals("first", fetch("/people/p/-/avatar"))
        assertEquals("within max-age nothing is asked", 1, seen.size)

        offset = 400_000
        answers += MockResponse().setResponseCode(304).addHeader("ETag", "\"a\"")
        assertEquals("a 304 keeps the held bytes", "first", fetch("/people/p/-/avatar"))
        assertEquals("/people/p/-/avatar \"a\"", seen.last())

        offset = 100_000
        assertEquals("the 304 starts a new lifetime", "first", fetch("/people/p/-/avatar"))
        assertEquals(2, seen.size)

        offset = 400_000
        answers += answer("second", "\"b\"")
        assertEquals("a changed image replaces the held one", "second", fetch("/people/p/-/avatar"))
        assertEquals(3, seen.size)

        offset = 100_000
        assertEquals("second", fetch("/people/p/-/avatar"))
        assertEquals(3, seen.size)
    }

    @Test
    fun `a refused or failed answer does not replace the held image`() {
        answers += answer("first", "\"a\"")
        assertEquals("first", fetch("/people/p/-/avatar"))

        offset = 400_000
        answers += MockResponse().setResponseCode(503)
        assertTrue(runCatching { fetch("/people/p/-/avatar") }.isFailure)

        offset = 400_000
        answers += MockResponse().setResponseCode(304).addHeader("ETag", "\"a\"")
        assertEquals("the held image survives the failure", "first", fetch("/people/p/-/avatar"))
        assertEquals("/people/p/-/avatar \"a\"", seen.last())
    }

    @Test
    fun `offline a stale image is served from the cache`() {
        answers += answer("first", "\"a\"")
        assertEquals("first", fetch("/people/p/-/avatar"))
        offset = 400_000
        assertEquals("first", fetch("/people/p/-/avatar", online = false))
        assertEquals(1, seen.size)
    }

    @Test
    fun `an answer that names no lifetime is served from the cache as before`() {
        answers += answer("bytes", "\"x\"", control = null)
        assertEquals("bytes", fetch("/feeds/f/-/attachments/1/thumbnail"))
        offset += 30L * 24 * 3600 * 1000
        assertEquals("bytes", fetch("/feeds/f/-/attachments/1/thumbnail"))
        assertEquals(1, seen.size)
    }

    @Test
    fun `no-cache is asked after every time`() {
        answers += answer("bytes", "\"n\"", control = "no-cache")
        assertEquals("bytes", fetch("/x"))
        answers += MockResponse().setResponseCode(304)
        assertEquals("bytes", fetch("/x"))
        assertEquals(listOf("/x -", "/x \"n\""), seen.toList())
    }
}
