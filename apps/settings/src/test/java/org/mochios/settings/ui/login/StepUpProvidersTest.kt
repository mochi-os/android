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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.PasskeyManager
import org.mochios.settings.api.AccountApi
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * The identity check offers only the providers the server can confirm an
 * identity through, as the identity list marks them; a core that does not say
 * keeps offering them all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StepUpProvidersTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private var methods = """{"methods": {"oauth": {"state": "allowed"}}}"""

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path.orEmpty().removePrefix("/").substringBefore("?")) {
                    "settings/-/user/account/methods" -> json(methods)
                    "settings/-/user/account/oauth" -> json(
                        """{"identities": [{"provider": "google", "verify": true}, {"provider": "mastodon", "verify": true},
                           {"provider": "bluesky", "verify": false}, {"provider": "github"}]}""",
                    )
                    else -> MockResponse().setResponseCode(404)
                }
        }
        server.start()
    }

    @After
    fun end() = server.shutdown()

    private fun json(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "application/json")

    private fun client(): SettingsStepUpClient {
        val api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build()
            .create(AccountApi::class.java)
        return SettingsStepUpClient(api, PasskeyManager(context))
    }

    @Test
    fun `offers only the providers the server can confirm an identity through`() {
        assertEquals(listOf("google", "mastodon", "github"), runBlocking { client().oauthProviders() })
    }

    // After a run of wrong authenticator codes the server adds the emailed
    // code, which no method's state shows: the dialog asks for what the server
    // names, so an authenticator-only account is offered the email it needs.
    @Test
    fun `asks for the factors the server names over those the states imply`() {
        methods = """{"methods": {"email": {"state": "allowed"}, "totp": {"state": "required"}},
            "required": ["email", "totp"]}"""
        assertEquals(listOf("email", "totp"), runBlocking { client().methods() })
    }

    @Test
    fun `reads the states when the server names no factors`() {
        methods = """{"methods": {"email": {"state": "allowed"}, "totp": {"state": "required"}}}"""
        assertEquals(listOf("totp"), runBlocking { client().methods() })
    }
}
