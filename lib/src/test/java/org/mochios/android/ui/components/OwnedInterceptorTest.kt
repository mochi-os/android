// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.map.Mapper
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.Options
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.AssetHttpModule
import org.mochios.android.api.OWNED
import org.mochios.android.api.owned
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * The image loader as the app builds it, through the asset client: an image
 * named by one of the app's own, server-relative addresses goes to the server
 * with the session and its app's token, and an absolute URL - on the user's
 * server or not - goes without, since a peer may have chosen it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OwnedInterceptorTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var loader: ImageLoader

    private val jar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            listOf(Cookie.Builder().domain(url.host).path("/").name("session").value("s3cret").build())
    }

    private fun origin() = server.url("/").toString().trimEnd('/')

    @Before
    fun begin() {
        server = MockWebServer().apply { start() }
        val client = AssetHttpModule.assetClient(server = ::origin, token = { app -> "token-$app" }, jar = jar)
        // RelativeAssetUrlMapper's rule over the test server's origin.
        val relative = object : Mapper<String, String> {
            override fun map(data: String, options: Options): String? = if (owned(data)) origin() + data else null
        }
        loader = ImageLoader.Builder(context)
            .components {
                add(OwnedInterceptor())
                add(relative)
                add(OkHttpNetworkFetcherFactory(callFactory = { client }))
            }
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
    }

    @After
    fun end() = server.shutdown()

    private fun load(data: String): RecordedRequest {
        server.enqueue(MockResponse().setBody("not an image"))
        runBlocking { loader.execute(ImageRequest.Builder(context).data(data).build()) }
        return server.takeRequest(5, TimeUnit.SECONDS)!!
    }

    @Test
    fun `an own address goes with the session and its app's token`() {
        val recorded = load("/feeds/1abc/-/attachments/9")
        assertEquals("/feeds/1abc/-/attachments/9", recorded.path)
        assertEquals("Bearer token-feeds", recorded.getHeader("Authorization"))
        assertEquals("session=s3cret", recorded.getHeader("Cookie"))
        assertNull("the mark never leaves the phone", recorded.getHeader(OWNED))
    }

    @Test
    fun `an absolute URL naming an own file is not answered from what the session fetched`() {
        val directory = java.io.File(context.cacheDir, "owned-" + System.nanoTime())
        val cached = ImageLoader.Builder(context)
            .components {
                add(OwnedInterceptor())
                add(object : Mapper<String, String> {
                    override fun map(data: String, options: Options): String? = if (owned(data)) origin() + data else null
                })
                add(OkHttpNetworkFetcherFactory(callFactory = { AssetHttpModule.assetClient(server = ::origin, token = { "token-$it" }, jar = jar) }))
            }
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCache { DiskCache.Builder().directory(directory.toOkioPath()).build() }
            .build()
        repeat(2) { server.enqueue(MockResponse().setHeader("Cache-Control", "max-age=3600").setBody("private")) }
        runBlocking { cached.execute(ImageRequest.Builder(context).data("/feeds/1abc/-/attachments/9").build()) }
        assertEquals("session=s3cret", server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("Cookie"))
        runBlocking { cached.execute(ImageRequest.Builder(context).data(origin() + "/feeds/1abc/-/attachments/9").build()) }
        val again = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull("THE POINT: the absolute URL is asked for itself, not served the session's copy", again)
        assertNull(again!!.getHeader("Cookie"))
        directory.deleteRecursively()
    }

    @Test
    fun `an absolute URL on the user's server goes without either`() {
        val recorded = load(origin() + "/settings/-/account/delete?confirm=1")
        assertEquals("/settings/-/account/delete?confirm=1", recorded.path)
        assertNull("THE POINT: no token for the action the URL names", recorded.getHeader("Authorization"))
        assertNull("THE POINT: and no session", recorded.getHeader("Cookie"))
    }
}
