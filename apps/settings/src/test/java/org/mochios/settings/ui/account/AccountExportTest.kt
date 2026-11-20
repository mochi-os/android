// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.account

import android.net.Uri
import android.os.Looper
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
import org.mochios.android.auth.PasskeyManager
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
import org.robolectric.shadows.ShadowToast
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayOutputStream
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

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when (path.substringBefore("?")) {
                    "/settings/-/user/account/identity" -> MockResponse().setHeader("Content-Type", "application/json")
                        .setBody("""{"entity": "e1", "fingerprint": "f1", "username": "ada@example.com", "name": "Ada", "privacy": "public", "role": "user"}""")
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

    private companion object {
        const val BUNDLE = "PK-the-encrypted-bundle"
    }
}
