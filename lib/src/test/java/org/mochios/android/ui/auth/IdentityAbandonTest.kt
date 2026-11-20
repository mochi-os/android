// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.auth

import android.os.Looper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.AuthApi
import org.mochios.android.auth.AuthRepository
import org.mochios.android.auth.PasskeyManager
import org.mochios.android.auth.SessionManager
import org.mochios.android.auth.TokenApi
import org.mochios.android.i18n.FakeKeystore
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * "Use a different account" on the create-identity screen signs the account
 * that verified its code out and returns to the email step, on the same
 * server; and the server the sign-in validates is stored as its origin.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [ShadowCleartextPolicy::class])
class IdentityAbandonTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var session: SessionManager
    private lateinit var model: AuthViewModel

    @Before
    fun begin() {
        FakeKeystore.install()
        server = MockWebServer()
        server.start()
        ShadowCleartextPolicy.hosts = setOf(server.hostName)
        session = SessionManager(context)
        runBlocking { session.clearAll() }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val repository = AuthRepository(retrofit.create(AuthApi::class.java), retrofit.create(TokenApi::class.java), session)
        model = AuthViewModel(repository, session, PasskeyManager(context))
    }

    @After
    fun end() {
        ShadowCleartextPolicy.hosts = emptySet()
        server.shutdown()
    }

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < deadline) { "timed out at ${model.uiState.value}" }
            Thread.sleep(10)
        }
    }

    private fun origin() = server.url("/").toString().trimEnd('/')

    /** Validate the server, then verify a code for an account with no identity. */
    private fun signUp() {
        server.enqueue(MockResponse().setBody("""{"email": true}"""))
        model.updateServerUrl(origin() + "/login")
        model.validateServer()
        until { model.uiState.value.serverValidated }
        server.enqueue(
            MockResponse()
                .addHeader("Set-Cookie", "session=half-made; Path=/")
                .setBody("""{"has_identity": false}""")
        )
        model.updateCode("123456")
        model.verifyCode()
        until { model.uiState.value.needsIdentity }
    }

    @Test
    fun `the server is stored as its origin`() {
        signUp()
        assertEquals(origin(), runBlocking { session.serverUrl.first() })
    }

    @Test
    fun `abandoning the identity signs the half-made account out and returns to the email step`() {
        signUp()
        assertEquals("half-made", runBlocking { session.currentToken.first() })
        model.updateIdentityName("Ann")
        model.abandonIdentity()
        until { !model.uiState.value.needsIdentity }
        until { runBlocking { session.currentToken.first() } == null }
        assertNull(runBlocking { session.currentToken.first() })
        assertEquals("", model.uiState.value.identityName)
        assertTrue("the server step stays behind", model.uiState.value.serverValidated)
        assertEquals("on the same server", origin(), runBlocking { session.serverUrl.first() })
        assertFalse(model.uiState.value.authComplete)
    }
}
