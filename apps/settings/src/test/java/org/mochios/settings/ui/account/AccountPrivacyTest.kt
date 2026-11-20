// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.account

import android.os.Looper
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.PasskeyManager
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.Downloads
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.settings.api.AccountApi
import org.mochios.settings.ui.login.SettingsStepUpClient
import org.mochios.settings.ui.networks.FakeKeystore
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The account's directory switch moves when tapped, not once the server has
 * answered, takes no second tap while the change goes, and goes back when
 * the server refuses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountPrivacyTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private val updates = CopyOnWriteArrayList<String>()

    /** Holds the update's answer back while it is set. */
    @Volatile private var gate: CountDownLatch? = null

    /** Whether the server refuses the update. */
    @Volatile private var refuse = false

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?").removePrefix("/")
                return when (path) {
                    "settings/-/user/account/identity" ->
                        json("""{"entity": "e1", "fingerprint": "f1", "username": "ada@example.com", "name": "Ada", "privacy": "public", "role": "user"}""")
                    "settings/-/user/account/identity/update" -> {
                        updates += request.body.readUtf8()
                        gate?.await(5, TimeUnit.SECONDS)
                        if (refuse) {
                            MockResponse().setResponseCode(500).setBody("""{"error": "Not now"}""").setHeader("Content-Type", "application/json")
                        } else {
                            json("""{"ok": true}""")
                        }
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        AppContext.set(context)
        FakeKeystore.install()
    }

    @After
    fun end() {
        server.shutdown()
    }

    private fun json(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "application/json")

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    private fun model(): AccountViewModel {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val api = retrofit.create(AccountApi::class.java)
        val session = SessionManager(context)
        runBlocking { session.clearAll() }
        val made = AccountViewModel(api, SettingsStepUpClient(api, PasskeyManager(context)), Downloads(context, FileStore(context)))
        until { !made.uiState.value.isLoading && made.uiState.value.identity.username.isNotEmpty() }
        return made
    }

    @Test
    fun `the directory switch moves at once and takes no second tap while it goes`() {
        gate = CountDownLatch(1)
        val model = model()
        assertEquals("public", model.uiState.value.identity.privacy)
        model.setPrivacy("private")
        assertEquals("private", model.uiState.value.identity.privacy)
        model.setPrivacy("public")
        assertEquals("private", model.uiState.value.identity.privacy)
        gate!!.countDown()
        until { !model.uiState.value.isSaving }
        assertEquals("private", model.uiState.value.identity.privacy)
        assertEquals(1, updates.size)
    }

    @Test
    fun `the directory switch goes back when the server refuses, saying why`() {
        refuse = true
        val model = model()
        model.setPrivacy("private")
        until { !model.uiState.value.isSaving }
        assertEquals("public", model.uiState.value.identity.privacy)
        assertNotNull(model.uiState.value.error)
    }
}
