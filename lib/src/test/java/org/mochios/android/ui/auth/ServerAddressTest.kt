// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.auth

import android.os.Looper
import android.security.NetworkSecurityPolicy
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.R
import org.mochios.android.api.MochiError
import org.mochios.android.auth.AuthApi
import org.mochios.android.auth.AuthRepository
import org.mochios.android.auth.PasskeyManager
import org.mochios.android.auth.SessionManager
import org.mochios.android.auth.TokenApi
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * The platform's network security policy, answering from [hosts]: plain HTTP
 * to those, HTTPS only everywhere else.
 */
@Implements(NetworkSecurityPolicy::class)
class ShadowCleartextPolicy {

    @Implementation
    fun isCleartextTrafficPermitted(hostname: String): Boolean = hostname in hosts

    companion object {
        var hosts: Set<String> = emptySet()
    }
}

/**
 * The sign-in screen takes a plain-HTTP server exactly where the platform's
 * network security policy allows plain HTTP to it - a debug build's localhost,
 * as the screenshot emulator uses - and refuses it everywhere else with its
 * own message, before any request.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [ShadowCleartextPolicy::class])
class ServerAddressTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer

    @Before
    fun begin() {
        server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"email": true}"""))
        server.start()
    }

    @After
    fun end() {
        ShadowCleartextPolicy.hosts = emptySet()
        server.shutdown()
    }

    private fun model(): AuthViewModel {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val session = SessionManager(context)
        val repository = AuthRepository(retrofit.create(AuthApi::class.java), retrofit.create(TokenApi::class.java), session)
        return AuthViewModel(repository, session, PasskeyManager(context))
    }

    /** Check the server at [url], and wait for the answer. */
    private fun validate(model: AuthViewModel, url: String) {
        model.updateServerUrl(url)
        model.validateServer()
        val deadline = System.currentTimeMillis() + 5_000
        while (model.uiState.value.isLoading) {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    @Test
    fun `a plain-HTTP server the policy allows is taken`() {
        ShadowCleartextPolicy.hosts = setOf(server.hostName)
        val model = model()
        validate(model, server.url("/").toString())
        assertEquals(null, model.uiState.value.error)
        assertTrue(model.uiState.value.serverValidated)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a plain-HTTP server the policy refuses is refused before any request`() {
        ShadowCleartextPolicy.hosts = setOf("elsewhere.example")
        val model = model()
        validate(model, server.url("/").toString())
        assertEquals(MochiError.Local(R.string.error_server_https), model.uiState.value.error)
        assertFalse(model.uiState.value.serverValidated)
        assertEquals(0, server.requestCount)
    }
}
