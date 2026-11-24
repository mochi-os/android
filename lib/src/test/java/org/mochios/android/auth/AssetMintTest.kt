// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.auth

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.FakeKeystore
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Base64

/**
 * The asset token a mint answers beside the app's own: kept in memory, used
 * while it has life left and was minted for the session held, and minted
 * again otherwise.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AssetMintTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var session: SessionManager
    private lateinit var auth: AuthRepository

    /** A JWT whose `exp` is [seconds] from now; [mark] tells two apart. */
    private fun jwt(seconds: Long, mark: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val payload = """{"exp": ${System.currentTimeMillis() / 1000 + seconds}, "mark": "$mark"}"""
        return encoder.encodeToString("{}".toByteArray()) + "." + encoder.encodeToString(payload.toByteArray()) + ".sig"
    }

    private fun minted(asset: String?) {
        val body = if (asset == null) """{"token": "${jwt(365L * 86400, "app")}"}""" else """{"token": "${jwt(365L * 86400, "app")}", "asset": "$asset"}"""
        server.enqueue(MockResponse().setBody(body))
    }

    @Before
    fun begin() {
        FakeKeystore.install()
        server = MockWebServer().apply { start() }
        session = SessionManager(context)
        runBlocking {
            session.clearAll()
            session.setServerUrl(server.url("/").toString())
            session.saveSession("s1")
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        auth = AuthRepository(retrofit.create(AuthApi::class.java), retrofit.create(TokenApi::class.java), session)
    }

    @After
    fun end() = server.shutdown()

    @Test
    fun `an asset token is minted once and then held`() {
        val asset = jwt(30 * 60, "first")
        minted(asset)
        assertEquals(asset, runBlocking { auth.asset("feeds") })
        assertEquals(asset, runBlocking { auth.asset("feeds") })
        assertEquals(1, server.requestCount)
        assertEquals("""{"app":"feeds"}""", server.takeRequest().body.readUtf8())
    }

    @Test
    fun `the app token's mint holds its asset token too`() {
        val asset = jwt(30 * 60, "first")
        minted(asset)
        runBlocking { auth.fetchToken("feeds", fresh = true) }
        assertEquals(asset, runBlocking { auth.asset("feeds") })
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `one near its end is minted again`() {
        minted(jwt(60, "ending"))
        val fresh = jwt(30 * 60, "fresh")
        minted(fresh)
        runBlocking { auth.asset("feeds") }
        assertEquals(fresh, runBlocking { auth.asset("feeds") })
    }

    @Test
    fun `one minted for another session is minted again`() {
        minted(jwt(30 * 60, "first"))
        val second = jwt(30 * 60, "second")
        minted(second)
        runBlocking { auth.asset("feeds") }
        runBlocking { session.saveSession("s2") }
        assertEquals(second, runBlocking { auth.asset("feeds") })
    }

    @Test
    fun `no asset token from a server that issues none, nor from a failed mint`() {
        minted(null)
        assertNull(runBlocking { auth.asset("feeds") })
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error": "x"}"""))
        assertNull(runBlocking { auth.asset("forums") })
    }

    /** Every owned image asks: were the answer not held, each would wait on a mint of its own. */
    @Test
    fun `a server's answer of none is held like a token`() {
        minted(null)
        minted(null)
        assertNull(runBlocking { auth.asset("feeds") })
        assertNull(runBlocking { auth.asset("feeds") })
        assertEquals("THE POINT: asked once", 1, server.requestCount)
    }

    @Test
    fun `no asset token without a session`() {
        runBlocking { session.clearAll() }
        assertNull(runBlocking { auth.asset("feeds") })
        assertEquals(0, server.requestCount)
    }
}
