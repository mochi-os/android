// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.login

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.android.auth.OAuthVerifyReturn
import org.mochios.android.auth.PasskeyManager
import org.mochios.settings.api.AccountApi
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URLDecoder

/**
 * An OAuth step-up begun from the app proves nothing in the browser the
 * consent ran in, which holds no session of ours: the server hands the app a
 * code through the deep link, and the app redeems it from its own session.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OAuthStepUpTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private val returns = OAuthVerifyReturn()
    private val finishes = mutableListOf<Map<String, String>>()
    private val begins = mutableListOf<Map<String, String>>()
    private var begun = """{"url": "https://accounts.example/consent", "nonce": "nonce-1"}"""
    private var finished = """{"token": "proof"}"""

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val form = form(request.body.readUtf8())
                return when (request.path.orEmpty().removePrefix("/").substringBefore("?")) {
                    "settings/-/user/account/oauth/verify/begin" -> {
                        begins += form
                        json(begun)
                    }
                    "settings/-/user/account/oauth/verify/finish" -> {
                        finishes += form
                        json(finished)
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun end() = server.shutdown()

    private fun json(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "application/json")

    private fun form(body: String): Map<String, String> = body.split('&').filter { it.contains('=') }.associate {
        val (key, value) = it.split('=', limit = 2)
        URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
    }

    private fun client(): SettingsStepUpClient {
        val api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build()
            .create(AccountApi::class.java)
        return SettingsStepUpClient(api, PasskeyManager(context), returns)
    }

    @Test
    fun `the begin names the app's scheme, and the code its return carries is redeemed with the verifier`() = runBlocking {
        val client = client()
        assertEquals("https://accounts.example/consent", client.oauthBegin("google"))
        assertEquals("mochi", begins.single()["scheme"])

        // Nothing has come back: nothing is asked of the server.
        assertNull(client.oauthPoll().token)
        assertTrue(finishes.isEmpty())

        // A return for another step-up is refused, and still nothing is asked.
        assertFalse(returns.deliver("forged", null, "nonce-2"))
        assertNull(client.oauthPoll().token)
        assertTrue(finishes.isEmpty())

        assertTrue(returns.deliver("code-1", null, "nonce-1"))
        assertEquals("proof", client.oauthPoll().token)
        val redeem = finishes.single()
        assertEquals("code-1", redeem["code"])
        assertTrue("the verifier the begin's challenge was made from", redeem["verifier"].orEmpty().length >= 43)
    }

    @Test
    fun `a refused consent ends the wait`() = runBlocking {
        val client = client()
        client.oauthBegin("google")
        assertTrue(returns.deliver(null, "reauthentication_failed", "nonce-1"))
        try {
            client.oauthPoll()
            fail("a refused return should end the wait")
        } catch (_: MochiError) {
        }
        assertTrue("a refusal is not redeemed", finishes.isEmpty())
    }

    @Test
    fun `a code the server will not redeem ends the wait`() = runBlocking {
        finished = "{}"
        val client = client()
        client.oauthBegin("google")
        assertTrue(returns.deliver("code-1", null, "nonce-1"))
        try {
            client.oauthPoll()
            fail("a spent code leaves nothing to wait for")
        } catch (_: MochiError) {
        }
        assertEquals(1, finishes.size)
    }

    // A server that answers no nonce proves the consent where the browser
    // finished it, so the app polls for the proof as it always did.
    @Test
    fun `a server that answers no nonce is polled for the proof`() = runBlocking {
        begun = """{"url": "https://accounts.example/consent"}"""
        val client = client()
        client.oauthBegin("google")
        assertEquals("proof", client.oauthPoll().token)
        assertNull(finishes.single()["code"])
        assertFalse("no return is waited for", returns.deliver("code-1", null, ""))
    }
}
