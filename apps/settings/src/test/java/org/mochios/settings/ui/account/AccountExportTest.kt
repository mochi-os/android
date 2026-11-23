// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.account

import android.net.Uri
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.userMessage
import org.mochios.android.auth.PasskeyManager
import org.mochios.android.files.Downloads
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.settings.R
import org.mochios.settings.api.AccountApi
import org.mochios.settings.ui.login.SettingsStepUpClient
import org.mochios.settings.ui.networks.FakeKeystore
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * A built export is fetched by the settings client into the document the user
 * chose: the request carries the app's own credential, and no other process
 * is handed one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountExportTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var downloads: Downloads

    /** Each export download asked for: its path and query, and its credential. */
    private val asked = CopyOnWriteArrayList<Pair<String, String?>>()

    /** The export action's answer. */
    @Volatile private var started = """{"state": "building", "file": ""}"""

    /** The status action's answers in turn; the last is kept. */
    private val statuses = ConcurrentLinkedQueue(listOf("""{"state": "none", "file": ""}"""))

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when (path.substringBefore("?")) {
                    "/settings/-/user/account/identity" -> MockResponse().setHeader("Content-Type", "application/json")
                        .setBody("""{"entity": "e1", "fingerprint": "f1", "username": "ada@example.com", "name": "Ada", "privacy": "public", "role": "user"}""")
                    "/settings/-/user/account/export" -> MockResponse().setHeader("Content-Type", "application/json").setBody(started)
                    "/settings/-/user/account/export/status" -> MockResponse().setHeader("Content-Type", "application/json")
                        .setBody(if (statuses.size > 1) statuses.poll()!! else statuses.peek()!!)
                    "/settings/-/user/account/export/download" -> {
                        asked += path to request.getHeader("Authorization")
                        if (path.contains("gone")) {
                            MockResponse().setResponseCode(400).setBody("""{"error": "Invalid file"}""")
                        } else {
                            MockResponse().setHeader("Content-Type", "application/zip").setBody(BUNDLE)
                        }
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        AppContext.set(context)
        FakeKeystore.install()
        downloads = Downloads(context, FileStore(context))
        ShadowToast.reset()
    }

    @After
    fun end() {
        server.shutdown()
    }

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    /** The view model over a client that signs its requests as the settings module's does. */
    private fun model(): AccountViewModel {
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer settings-token").build())
            })
            .build()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AccountApi::class.java)
        return AccountViewModel(api, SettingsStepUpClient(api, PasskeyManager(context)), downloads)
    }

    @Test
    fun `a built export is streamed into the chosen document through the settings client`() {
        val destination = Uri.parse("content://org.mochios.test/export.zip")
        val written = ByteArrayOutputStream()
        shadowOf(context.contentResolver).registerOutputStream(destination, written)
        model().saveExport("mochi-export-20261120-f1-a.zip", destination)
        until { downloads.running.value.isEmpty() && ShadowToast.getTextOfLatestToast() == "mochi-export-20261120-f1-a.zip saved" }
        assertEquals(BUNDLE, written.toString(Charsets.UTF_8.name()))
        assertEquals(
            listOf("/settings/-/user/account/export/download?file=mochi-export-20261120-f1-a.zip" to "Bearer settings-token"),
            asked,
        )
    }

    @Test
    fun `an export the server no longer holds is said to be lost`() {
        val destination = Uri.parse("content://org.mochios.test/gone.zip")
        shadowOf(context.contentResolver).registerOutputStream(destination, ByteArrayOutputStream())
        model().saveExport("gone.zip", destination)
        until { ShadowToast.getTextOfLatestToast() == "Couldn't save gone.zip: Invalid file" }
    }

    /** Starts an export as the screen does, and settles its step-up. */
    private fun AccountViewModel.start() {
        exportData("correct horse battery staple")
        stepUp.onVerified("proof")
    }

    /** What the view model emits, collected from before the action. */
    private fun <T> SharedFlow<T>.collected(): List<T> {
        val out = CopyOnWriteArrayList<T>()
        CoroutineScope(Dispatchers.Unconfined).launch { collect { out += it } }
        return out
    }

    private fun AccountViewModel.watch() = runBlocking { withTimeout(5_000) { watchExport() } }

    @Test
    fun `a bundle the server built at once is saved straight away`() {
        started = """{"state": "ready", "file": "b.zip", "filename": "b.zip"}"""
        val model = model()
        val ready = model.exportReady.collected()
        model.start()
        until { ready.isNotEmpty() }
        assertEquals(listOf("b.zip"), ready)
        assertEquals("and offered, for a save backed out of", Export.Ready("b.zip"), model.uiState.value.export)
    }

    @Test
    fun `an older server's answer, the filename alone, is saved as before`() {
        started = """{"filename": "old.zip"}"""
        val model = model()
        val ready = model.exportReady.collected()
        model.start()
        until { ready.isNotEmpty() }
        assertEquals(listOf("old.zip"), ready)
    }

    @Test
    fun `a bundle still being built is followed until it is ready, and then offered`() {
        val model = model()
        model.pollMillis = 10
        val ready = model.exportReady.collected()
        val notices = model.notices.collected()
        model.start()
        until { model.uiState.value.export == Export.Building }
        statuses.clear()
        statuses += listOf("""{"state": "building", "file": ""}""", """{"state": "ready", "file": "b.zip"}""")
        model.watch()
        assertEquals("THE POINT: followed to the end", Export.Ready("b.zip"), model.uiState.value.export)
        assertEquals(listOf(R.string.account_data_ready), notices)
        assertEquals("offered, not saved unasked", emptyList<String>(), ready)

        val destination = Uri.parse("content://org.mochios.test/b.zip")
        shadowOf(context.contentResolver).registerOutputStream(destination, ByteArrayOutputStream())
        model.saveExport("b.zip", destination)
        assertEquals("the download consumes it", Export.None, model.uiState.value.export)
    }

    @Test
    fun `a build that fails while watched says why`() {
        val model = model()
        model.pollMillis = 10
        model.start()
        until { model.uiState.value.export == Export.Building }
        statuses.clear()
        statuses += """{"state": "failed", "file": "", "error": "The export failed: the disk is full"}"""
        model.watch()
        assertEquals(Export.None, model.uiState.value.export)
        assertEquals("The export failed: the disk is full", model.uiState.value.error?.userMessage())
    }

    @Test
    fun `opened, it offers a bundle built while it was away and does not repeat an earlier failure`() {
        statuses.clear()
        statuses += """{"state": "ready", "file": "away.zip"}"""
        val model = model()
        val notices = model.notices.collected()
        model.watch()
        assertEquals(Export.Ready("away.zip"), model.uiState.value.export)
        assertEquals("nothing said for a build it did not watch", emptyList<Int>(), notices)

        statuses.clear()
        statuses += """{"state": "failed", "file": "", "error": "The export failed: the disk is full"}"""
        val again = model()
        again.watch()
        assertEquals(Export.None, again.uiState.value.export)
        assertEquals(null, again.uiState.value.error)
    }

    private companion object {
        const val BUNDLE = "PK-the-encrypted-bundle"
    }
}
