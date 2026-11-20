// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.repository

import android.os.Looper
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.ApiClient
import org.mochios.android.auth.SessionManager
import org.mochios.feeds.api.FeedsApi
import org.mochios.feeds.model.SavedSnapshot
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * A saved post's snapshot is written by whichever client saved it, and one
 * that leaves a field out (the web's leaves out an empty caption) or sends it
 * null once crashed the Saved mosaic with a null in a non-null field. The
 * app's Gson gives each such field its default, so the snapshot reads whole.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SavedSnapshotDefaultsTest {

    // The web's snapshot of a two-image post: no caption on either image, and
    // an explicit null name and caption on the second.
    private val json = """
        {"id": "p1", "attachments": [
            {"id": "a1", "name": "one.jpg", "type": "image/jpeg"},
            {"id": "a2", "name": null, "type": "image/png", "caption": null}
        ]}
    """.trimIndent()

    @Test
    fun `a missing or null caption reads as empty through the app's Gson`() {
        val snapshot = ApiClient.provideGson().fromJson(json, SavedSnapshot::class.java)
        assertEquals("", snapshot.attachments[0].caption)
        assertEquals("THE POINT: an explicit null takes the default too", "", snapshot.attachments[1].caption)
        assertEquals("", snapshot.attachments[1].name)
    }

    @Test
    fun `the fields that were there are kept`() {
        val snapshot = ApiClient.provideGson().fromJson(json, SavedSnapshot::class.java)
        assertEquals("one.jpg", snapshot.attachments[0].name)
        assertEquals("image/png", snapshot.attachments[1].type)
    }

    @Test
    fun `the saved list reads its snapshots whole`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"data": {"saved": [{"post": $json, "created": 1}]}}"""))
        server.start()
        try {
            val context = RuntimeEnvironment.getApplication()
            val gson = ApiClient.provideGson()
            // As the app builds the feeds API: on the injected Gson.
            val api = Retrofit.Builder()
                .baseUrl(server.url("/feeds/"))
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(FeedsApi::class.java)
            val session = SessionManager(context)
            runBlocking { session.setBoundAccount("a1") }
            val saved = SavedRepository(api, gson, session)
            runBlocking { saved.load() }
            val deadline = System.currentTimeMillis() + 5_000
            while (saved.saved.value.isEmpty()) {
                shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
                if (System.currentTimeMillis() > deadline) throw AssertionError("timed out")
                Thread.sleep(10)
            }
            val attachments = saved.saved.value.single().post.attachments
            assertEquals("THE POINT: the captions come back empty, for the mosaic", listOf("", ""), attachments.map { it.caption })
            assertEquals("", attachments[1].name)
        } finally {
            server.shutdown()
        }
    }
}
