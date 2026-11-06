// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.fetch.SourceFetchResult
import coil3.network.DeDupeConcurrentRequestStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.Options
import coil3.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
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
import java.util.concurrent.TimeUnit

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
    @Volatile private var offset = 0L

    /** Run as the server answers each request. */
    @Volatile private var answering: () -> Unit = {}
    private val answers = java.util.Collections.synchronizedList(mutableListOf<MockResponse>())
    private val seen = java.util.Collections.synchronizedList(mutableListOf<String>())

    @Before
    fun begin() {
        server = MockWebServer()
        // Answers in the order enqueued, and records every request, so one the
        // cache should have answered shows as a failure rather than a wait: a
        // refusal, which a failing server's stand-in would not cover.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += "${request.path} ${request.getHeader("If-None-Match") ?: "-"}"
                answering()
                return answers.removeFirstOrNull() ?: MockResponse().setResponseCode(418)
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

    // As the app builds it. No second try on a dropped connection, so one
    // answer meets one request.
    private val factory by lazy {
        OkHttpNetworkFetcherFactory(
            callFactory = { OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor(StaleIfError()).build() },
            cacheStrategy = { RevalidatingCacheStrategy { System.currentTimeMillis() + offset } },
            concurrentRequestStrategy = { DeDupeConcurrentRequestStrategy() },
        )
    }

    // Coil refuses a network fetch on the main thread, which the test's is.
    private fun fetch(path: String, online: Boolean = true): String = runBlocking(Dispatchers.IO) { load(path, online) }

    private suspend fun load(path: String, online: Boolean = true): String {
        // Offline the loader may not use the network, and asks for the held
        // copy alone.
        val options = if (online) Options(context) else Options(context, networkCachePolicy = CachePolicy.DISABLED)
        val fetcher = factory.create(server.url(path).toString().toUri(), options, loader)!!
        val result = fetcher.fetch() as SourceFetchResult
        // Closed as a decoder closes it: an open source holds its cache entry,
        // which the next answer could not then be written over.
        return result.source.use { it.source().readUtf8() }
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

    // A friend's server that is offline answers its avatar 502 through ours.
    @Test
    fun `a server that fails the asking leaves the held image shown`() {
        answers += answer("first", "\"a\"")
        assertEquals("first", fetch("/people/p/-/avatar"))

        offset = 400_000
        answers += MockResponse().setResponseCode(502)
        assertEquals("a failing server keeps the held image", "first", fetch("/people/p/-/avatar"))
        assertEquals("/people/p/-/avatar \"a\"", seen.last())

        answers += MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
        assertEquals("no answer keeps the held image", "first", fetch("/people/p/-/avatar"))
        assertEquals(3, seen.size)

        // Asked after again once a lifetime from the failure has passed, not
        // on every load while the server is down.
        offset = 100_000
        assertEquals("first", fetch("/people/p/-/avatar"))
        assertEquals(3, seen.size)
        offset = 400_000
        answers += MockResponse().setResponseCode(304).addHeader("ETag", "\"a\"")
        assertEquals("first", fetch("/people/p/-/avatar"))
        assertEquals(4, seen.size)
    }

    // A wiki thread's comments by one author share one avatar address. Loads
    // asking together each held the cached copy open, so none could store the
    // answer, and the loader asked again with no validator, which the failing
    // server refused: the placeholder showed after all.
    @Test
    fun `loads of one stale image at once ask once and all show the held copy`() {
        answers += answer("first", "\"a\"")
        assertEquals("first", fetch("/people/p/-/avatar"))

        offset = 400_000
        // The clock stands still once the server answers: the held copy's new
        // lifetime starts then, and the loads waiting on the first find it fresh.
        answering = { offset = 0 }
        repeat(3) { answers += MockResponse().setResponseCode(502).setHeadersDelay(500, TimeUnit.MILLISECONDS) }
        val shown = runBlocking(Dispatchers.IO) { List(3) { async { load("/people/p/-/avatar") } }.awaitAll() }
        assertEquals(listOf("first", "first", "first"), shown)
        assertEquals("asked once for all of them", 2, seen.size)
    }

    @Test
    fun `a refusal fails the load but does not replace the held image`() {
        answers += answer("first", "\"a\"")
        assertEquals("first", fetch("/people/p/-/avatar"))

        offset = 400_000
        answers += MockResponse().setResponseCode(404)
        assertTrue(runCatching { fetch("/people/p/-/avatar") }.isFailure)

        offset = 400_000
        answers += MockResponse().setResponseCode(304).addHeader("ETag", "\"a\"")
        assertEquals("the held image survives the refusal", "first", fetch("/people/p/-/avatar"))
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
