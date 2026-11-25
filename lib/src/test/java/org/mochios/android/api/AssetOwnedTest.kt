// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.api

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * The asset client sends a credential only with a request for one of the
 * app's own, server-relative addresses, and then the app's asset token, which
 * core honours only to read the actions an app marks as serving assets. An
 * absolute URL on the user's server may be a peer's choice - a link card, a
 * page's image, a feed's enclosure - and with the credentials it would run
 * whatever action of whatever app its path named, as the user.
 */
class AssetOwnedTest {

    private lateinit var server: MockWebServer
    private lateinit var elsewhere: MockWebServer

    /** The session's jar. */
    private val jar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
        // As SessionManager's: the session for the user's server alone.
        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            if (url.port != server.port) emptyList()
            else listOf(Cookie.Builder().domain(url.host).path("/").name("session").value("s3cret").build())
    }

    private fun origin() = server.url("/").toString().trimEnd('/')

    /** The asset tokens the client holds, by app; none for an app not listed. */
    private var assets: (String) -> String? = { app -> "asset-$app" }

    private fun client() = AssetHttpModule.assetClient(
        server = ::origin,
        token = { app -> "token-$app" },
        asset = { app -> assets(app) },
        jar = jar,
    )

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        elsewhere = MockWebServer().apply { start() }
    }

    @After
    fun stop() {
        server.shutdown()
        elsewhere.shutdown()
    }

    private fun fetch(url: String, headers: Map<String, String> = emptyMap()): RecordedRequest {
        val target = if (url.startsWith(elsewhere.url("/").toString())) elsewhere else server
        target.enqueue(MockResponse().setBody("x"))
        val request = Request.Builder().url(url)
        for ((name, value) in headers) request.header(name, value)
        client().newCall(request.build()).execute().close()
        return target.takeRequest(5, TimeUnit.SECONDS)!!
    }

    @Test
    fun `an own address is a server-relative one, and nothing else`() {
        assertTrue(owned("/feeds/1abc/-/attachments/9"))
        assertTrue(owned("/people/1abc/-/avatar"))
        assertFalse("absolute, even on the user's server", owned("https://mochi.example/feeds/1abc/-/attachments/9"))
        assertFalse("protocol-relative names another host", owned("//attacker.example/x"))
        assertFalse(owned("attachments/9"))
        assertFalse(owned(""))
        assertFalse("no address the app builds holds a backslash", owned("/\\attacker.example/x"))
        assertFalse("nor a control character", owned("/feeds/1abc/-/a\n"))
        assertFalse("a dot segment sends it to another app's path", owned("/people/../settings/-/account/delete"))
        assertFalse("escaped as well", owned("/people/%2E%2e/settings/-/x"))
        assertFalse(owned("/feeds/./x"))
        assertTrue("a query is its own", owned("/people/1abc/-/avatar?v=3"))
        assertTrue("dots inside a name are not a segment", owned("/feeds/1abc/-/attachments/a..b"))
    }

    // The only way a String becomes one of the app's own, which the asset
    // client credentials: through the check that it is one.
    @Test
    fun `an address is made the app's own only when it is one`() {
        assertEquals("/feeds/1abc/-/attachments/9", OwnedAddress.of("/feeds/1abc/-/attachments/9")?.address)
        assertNull(OwnedAddress.of("https://mochi.example/feeds/1abc/-/attachments/9"))
        assertNull(OwnedAddress.of("/people/../settings/-/x"))
        assertNull(OwnedAddress.of(null))
    }

    @Test
    fun `an own address resolves onto the server, any other stays as given`() {
        assertEquals("https://mochi.example/feeds/1/-/a", resolved("https://mochi.example/", "/feeds/1/-/a"))
        assertEquals("https://attacker.example/x", resolved("https://mochi.example", "https://attacker.example/x"))
        assertEquals(mapOf(OWNED to "1"), assetHeaders(OwnedAddress.of("/chat/1/-/attachments/9")))
        assertEquals(emptyMap<String, String>(), assetHeaders(OwnedAddress.of("https://mochi.example/chat/1/-/attachments/9")))
    }

    @Test
    fun `a marked request for the user's server carries its app's asset token and nothing else`() {
        val recorded = fetch(origin() + "/forums/1abc/-/attachments/9", assetHeaders(OwnedAddress.of("/forums/1abc/-/attachments/9")))
        assertEquals("Bearer asset-forums", recorded.getHeader("Authorization"))
        assertNull("THE POINT: no session", recorded.getHeader("Cookie"))
        assertNull("the mark never leaves the phone", recorded.getHeader(OWNED))
    }

    // The people app marks its banner and favicon as assets, like every image
    // route the client loads: they take the asset token too.
    @Test
    fun `a person's banner and favicon carry the asset token and no session`() {
        for (image in listOf("banner", "favicon")) {
            val path = "/people/1abc/-/$image"
            val recorded = fetch(origin() + path, assetHeaders(OwnedAddress.of(path)))
            assertEquals(image, "Bearer asset-people", recorded.getHeader("Authorization"))
            assertNull("THE POINT: no session for the $image", recorded.getHeader("Cookie"))
        }
    }

    @Test
    fun `an app no asset token can be had for goes with the session and the app's token`() {
        assets = { null }
        val recorded = fetch(origin() + "/forums/1abc/-/attachments/9", assetHeaders(OwnedAddress.of("/forums/1abc/-/attachments/9")))
        assertEquals("Bearer token-forums", recorded.getHeader("Authorization"))
        assertEquals("session=s3cret", recorded.getHeader("Cookie"))
    }

    @Test
    fun `an unmarked request for the user's server carries neither`() {
        // An absolute URL a peer chose, naming another app's action.
        val recorded = fetch(origin() + "/settings/-/account/delete?confirm=1")
        assertNull("THE POINT: no token for the action a peer named", recorded.getHeader("Authorization"))
        assertNull("THE POINT: and no session", recorded.getHeader("Cookie"))
    }

    @Test
    fun `a foreign host gets neither, marked or not`() {
        val recorded = fetch(elsewhere.url("/feeds/1abc/-/attachments/9").toString(), mapOf(OWNED to "1"))
        assertNull(recorded.getHeader("Authorization"))
        assertNull(recorded.getHeader("Cookie"))
        assertNull(recorded.getHeader(OWNED))
        assertTrue("a browser's agent, which CDNs require", recorded.getHeader("User-Agent")!!.contains("Mozilla"))
    }

    @Test
    fun `a redirect from an own address to another host takes neither with it`() {
        // A commenter's avatar route redirects to the icon URL they set.
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", elsewhere.url("/icon.png").toString()))
        elsewhere.enqueue(MockResponse().setBody("x"))
        val request = Request.Builder().url(origin() + "/feeds/1abc/-/post/comment/asset/avatar")
        for ((name, value) in assetHeaders(OwnedAddress.of("/feeds/1abc/-/post/comment/asset/avatar"))) request.header(name, value)
        client().newCall(request.build()).execute().close()
        val first = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("the own address itself is credentialed", "Bearer asset-feeds", first.getHeader("Authorization"))
        val hop = elsewhere.takeRequest(5, TimeUnit.SECONDS)!!
        assertNull("THE POINT: the session does not follow the redirect", hop.getHeader("Cookie"))
        assertNull(hop.getHeader("Authorization"))
        assertNull(hop.getHeader(OWNED))
    }

    @Test
    fun `a redirect from an own address to another path on the server takes neither with it`() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/settings/-/account/delete?confirm=1"))
        server.enqueue(MockResponse().setBody("x"))
        val request = Request.Builder().url(origin() + "/feeds/1abc/-/post/comment/asset/avatar")
        for ((name, value) in assetHeaders(OwnedAddress.of("/feeds/1abc/-/post/comment/asset/avatar"))) request.header(name, value)
        client().newCall(request.build()).execute().close()
        assertEquals("Bearer asset-feeds", server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("Authorization"))
        val hop = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/settings/-/account/delete?confirm=1", hop.path)
        assertNull("THE POINT: no token for the action the answer named", hop.getHeader("Authorization"))
        assertNull("THE POINT: and no session", hop.getHeader("Cookie"))
    }

    /** The client for addresses written into a post, comment or wiki page. */
    private fun anonymous() = AssetHttpModule.anonymousClient(::origin, client())

    @Test
    fun `the anonymous client sends neither to the user's server, even for a marked request`() {
        server.enqueue(MockResponse().setBody("x"))
        val request = Request.Builder().url(origin() + "/settings/-/account/delete?confirm=1").header(OWNED, "1")
        anonymous().newCall(request.build()).execute().close()
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertNull("THE POINT: no token for the action a body named", recorded.getHeader("Authorization"))
        assertNull("THE POINT: and no session", recorded.getHeader("Cookie"))
        assertNull("the mark never leaves the phone", recorded.getHeader(OWNED))
    }

    @Test
    fun `the anonymous client gives a foreign host a browser's headers and no credentials`() {
        elsewhere.enqueue(MockResponse().setBody("x"))
        anonymous().newCall(Request.Builder().url(elsewhere.url("/a.png")).build()).execute().close()
        val recorded = elsewhere.takeRequest(5, TimeUnit.SECONDS)!!
        assertNull(recorded.getHeader("Authorization"))
        assertNull(recorded.getHeader("Cookie"))
        assertTrue(recorded.getHeader("User-Agent")!!.contains("Mozilla"))
    }

}
