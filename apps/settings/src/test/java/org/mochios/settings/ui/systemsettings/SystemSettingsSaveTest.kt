// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.systemsettings

import android.net.Uri
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.android.api.userMessage
import org.mochios.android.auth.PasskeyManager
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.settings.R
import org.mochios.settings.api.AccountApi
import org.mochios.settings.api.SystemSettingsApi
import org.mochios.settings.ui.login.SettingsStepUpClient
import org.mochios.settings.ui.networks.FakeKeystore
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A setting saved on the tap shows the value asked for until it is saved, and
 * the stored one again when the step-up is cancelled or the server refuses;
 * the list stays on screen while it is read again; a file setting is set from
 * a document's text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SystemSettingsSaveTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private val saved = CopyOnWriteArrayList<String>()

    /** Whether the server refuses the save. */
    @Volatile private var refuse = false

    /** Holds the list's answer back while it is set. */
    @Volatile private var gate: CountDownLatch? = null

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path.orEmpty().substringBefore("?").removePrefix("/")) {
                    "settings/-/system/settings/list" -> {
                        gate?.await(5, TimeUnit.SECONDS)
                        json(
                            """{"settings": [
                                {"name": "signup_enabled", "value": "true", "default": "true", "pattern": "^(true|false)$"},
                                {"name": "fcm.firebase_config", "value": "", "default": "", "pattern": "text"}]}""",
                        )
                    }
                    "settings/-/system/settings/set" -> {
                        saved += URLDecoder.decode(request.body.readUtf8(), "UTF-8")
                        if (refuse) {
                            MockResponse().setResponseCode(400).setHeader("Content-Type", "application/json")
                                .setBody("""{"error": "You must keep at least one way to sign in"}""")
                        } else {
                            json("""{"ok": true}""")
                        }
                    }
                    else -> MockResponse().setResponseCode(404)
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

    private fun model(): SystemSettingsViewModel {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val made = SystemSettingsViewModel(
            retrofit.create(SystemSettingsApi::class.java),
            SettingsStepUpClient(retrofit.create(AccountApi::class.java), PasskeyManager(context)),
            FileStore(context),
        )
        until { made.uiState.value.settings.isNotEmpty() }
        return made
    }

    private fun SystemSettingsViewModel.stored(name: String) =
        uiState.value.settings.first { it.name == name }.value

    @Test
    fun `a cancelled step-up puts the control back`() {
        val model = model()
        model.setValue("signup_enabled", "false")
        assertEquals("the switch moves on the tap", "false", model.uiState.value.pending["signup_enabled"])
        assertTrue(model.stepUp.visible.value)
        model.stepUp.cancel()
        assertNull("THE POINT: the switch goes back", model.uiState.value.pending["signup_enabled"])
        assertEquals("true", model.stored("signup_enabled"))
        assertEquals(emptyList<String>(), saved)
    }

    @Test
    fun `a refused save puts the control back and says why`() {
        refuse = true
        val model = model()
        model.setValue("signup_enabled", "false")
        model.stepUp.onVerified("proof")
        until { model.uiState.value.error != null }
        assertNull("THE POINT: the switch goes back", model.uiState.value.pending["signup_enabled"])
        assertEquals("true", model.stored("signup_enabled"))
        assertEquals("You must keep at least one way to sign in", model.uiState.value.error!!.userMessage())
        model.clearError()
        assertNull(model.uiState.value.error)
    }

    @Test
    fun `a saved value is kept, and the list stays on screen while it is read again`() {
        val model = model()
        gate = CountDownLatch(1)
        model.setValue("signup_enabled", "false")
        model.stepUp.onVerified("proof")
        until { saved.isNotEmpty() && model.uiState.value.savingName == null }
        assertEquals("false", model.stored("signup_enabled"))
        assertNull(model.uiState.value.pending["signup_enabled"])
        assertFalse("THE POINT: no spinner over the list while it is read again", model.uiState.value.isLoading)
        assertTrue(model.uiState.value.settings.isNotEmpty())
        gate!!.countDown()
    }

    @Test
    fun `a file setting is set to the text of the picked document`() {
        val document = Uri.parse("content://org.mochios.test/firebase.json")
        shadowOf(context.contentResolver).registerInputStream(document, ByteArrayInputStream("""{"project_id": "p1"}""".toByteArray()))
        val model = model()
        model.upload("fcm.firebase_config", document)
        until { model.stepUp.visible.value }
        assertEquals("""{"project_id": "p1"}""", model.uiState.value.pending["fcm.firebase_config"])
        model.stepUp.onVerified("proof")
        until { saved.isNotEmpty() }
        assertTrue(saved.single().contains("""value={"project_id": "p1"}"""))
    }

    @Test
    fun `a refused save is said over the list`() {
        refuse = true
        val model = model()
        rule.setContent { SystemSettingsScreen(onBack = {}, viewModel = model) }
        model.setValue("signup_enabled", "false")
        model.stepUp.onVerified("proof")
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("You must keep at least one way to sign in").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Allow new signups", substring = true, ignoreCase = true).assertExists()
    }

    @Test
    fun `a document that cannot be read is said, and nothing is saved`() {
        val model = model()
        model.upload("fcm.firebase_config", Uri.parse("content://org.mochios.test/missing.json"))
        until { model.uiState.value.error != null }
        assertEquals(MochiError.Local(R.string.system_settings_read_failed), model.uiState.value.error)
        assertFalse(model.stepUp.visible.value)
    }
}
