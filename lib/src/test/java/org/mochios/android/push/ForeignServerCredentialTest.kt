// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mochios.android.api.foreignClient
import org.mochios.android.api.serverInterceptor
import org.mochios.android.util.isServerOrigin

/**
 * The push distributor holds one subscription per Mochi identity, and the
 * identities may live on different servers. Everything it sends for account B
 * must reach B: built from the shared client instead, the bound-server
 * retarget delivers it to A along with B's credential.
 */
class ForeignServerCredentialTest {

    private lateinit var bound: MockWebServer
    private lateinit var other: MockWebServer

    @Before
    fun start() {
        bound = MockWebServer().apply { start() }
        other = MockWebServer().apply { start() }
    }

    @After
    fun stop() {
        bound.shutdown()
        other.shutdown()
    }

    private fun url(server: MockWebServer) = server.url("/").toString().trimEnd('/')

    private fun call(client: OkHttpClient, server: MockWebServer) {
        val request = Request.Builder()
            .url(url(server) + "/notifications/-/push/drain")
            .header("Authorization", "Bearer token-for-the-other-server")
            .build()
        client.newCall(request).execute().close()
    }

    @Test
    fun `a call for another account's server reaches that server`() {
        other.enqueue(MockResponse().setBody("{}"))
        call(foreignClient().build(), other)

        assertEquals("the other server must receive it", 1, other.requestCount)
        assertEquals("the bound server must not see it", 0, bound.requestCount)
    }

    /**
     * Negative control. The shared client's outermost interceptor is the
     * bound-server retarget, and `newBuilder()` copies it - which is exactly
     * how the credential used to arrive at the wrong host.
     */
    @Test
    fun `built from the shared client the same call lands on the bound server`() {
        val shared = OkHttpClient.Builder()
            .addInterceptor(serverInterceptor { url(bound) })
            .build()

        bound.enqueue(MockResponse().setBody("{}"))
        call(shared.newBuilder().build(), other)

        assertEquals("reproduces the bug: the bound server got it", 1, bound.requestCount)
        assertNotEquals("and the intended server never did", 1, other.requestCount)
        assertEquals(
            "carrying the other account's bearer token",
            "Bearer token-for-the-other-server",
            bound.takeRequest().getHeader("Authorization"),
        )
    }

    // ---------------- the mint cookie jar ----------------

    /** The jar `mintToken` installs: this account's session, its origin only. */
    private fun jar(server: String, value: String) = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {}
        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            if (isServerOrigin(url, server)) {
                listOf(
                    Cookie.Builder().domain(url.host).path("/")
                        .name("session").value(value).build()
                )
            } else {
                emptyList()
            }
    }

    @Test
    fun `the mint session reaches its own server`() {
        other.enqueue(MockResponse().setBody("{}"))
        val client = foreignClient().cookieJar(jar(url(other), "session-b")).build()
        client.newCall(Request.Builder().url(url(other) + "/_/token").build()).execute().close()

        assertEquals("session=session-b", other.takeRequest().getHeader("Cookie"))
    }

    /**
     * A redirect off the account's origin must not carry the session with it.
     * The old jar returned the cookie for any URL at all.
     */
    @Test
    fun `the mint session does not follow a redirect to another server`() {
        other.enqueue(
            MockResponse().setResponseCode(302)
                .setHeader("Location", url(bound) + "/_/token")
        )
        bound.enqueue(MockResponse().setBody("{}"))

        val client = foreignClient().cookieJar(jar(url(other), "session-b")).build()
        client.newCall(Request.Builder().url(url(other) + "/_/token").build()).execute().close()

        other.takeRequest()
        assertNull(
            "the other server must not be handed this session",
            bound.takeRequest().getHeader("Cookie"),
        )
    }

    /** Negative control for the jar: the unscoped form hands it over. */
    @Test
    fun `an unscoped jar hands the session to whichever server it is redirected to`() {
        other.enqueue(
            MockResponse().setResponseCode(302)
                .setHeader("Location", url(bound) + "/_/token")
        )
        bound.enqueue(MockResponse().setBody("{}"))

        val unscoped = object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {}
            override fun loadForRequest(url: HttpUrl): List<Cookie> = listOf(
                Cookie.Builder().domain(url.host).path("/")
                    .name("session").value("session-b").build()
            )
        }
        val client = foreignClient().cookieJar(unscoped).build()
        client.newCall(Request.Builder().url(url(other) + "/_/token").build()).execute().close()

        other.takeRequest()
        assertEquals(
            "reproduces the bug",
            "session=session-b",
            bound.takeRequest().getHeader("Cookie"),
        )
    }

    @Test
    fun `the foreign client asks for json`() {
        other.enqueue(MockResponse().setBody("{}"))
        foreignClient().build()
            .newCall(Request.Builder().url(url(other) + "/x").build()).execute().close()

        assertEquals("application/json", other.takeRequest().getHeader("Accept"))
    }
}
