// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.settings

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.feeds.api.FeedsApi
import org.mochios.feeds.api.MenuApi
import org.mochios.feeds.model.Feed
import org.mochios.feeds.repository.FeedsRepository
import org.mochios.feeds.ui.find.FindFeedsViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * The consent a source, a waiting server or a follow needs opens only on a
 * permission the server names: a lookup the server refuses, or one it
 * answers with no name, is reported where each screen reports its other
 * failures, and the raw permission code is never put to the user.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PermissionLookupTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var repository: FeedsRepository
    private lateinit var session: SessionManager

    /** What `-/permissions/name` answers. */
    @Volatile private var naming: () -> MockResponse = { ok("""{"name": "Fetch from example.org"}""") }

    private val refused = MockResponse().setResponseCode(403).setBody(
        """{"error": "permission_required", "app": "feeds", "permission": "url:example.org"}""",
    )

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path.orEmpty().substringBefore("?")) {
                    "/feeds/f1/-/info" -> ok(
                        """{"feed": {"id": "f1", "fingerprint": "f1", "name": "Feed"}, "permissions": {"manage": true}}""",
                    )
                    "/feeds/-/accounts/list" -> MockResponse().setBody("[]")
                    "/feeds/f1/-/sources/add", "/feeds/f1/-/sources/allow" -> refused
                    "/feeds/-/permissions/name" -> naming()
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/feeds/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        session = SessionManager(context)
        runBlocking {
            session.setServerUrl(server.url("/").toString().trimEnd('/'))
            session.setBoundAccount("a1")
        }
        repository = FeedsRepository(
            retrofit.create(FeedsApi::class.java),
            retrofit.create(MenuApi::class.java),
            FileStore(context),
            context,
            session,
        )
    }

    @After
    fun end() = server.shutdown()

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun unnamed() {
        naming = { MockResponse().setResponseCode(404).setBody("""{"error": "not_found", "message": "Not found"}""") }
    }

    private fun blank() {
        naming = { ok("""{"name": ""}""") }
    }

    /** Runs the main thread until [done]; an assertion fails if it never is. */
    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            if (System.currentTimeMillis() > deadline) throw AssertionError("timed out")
            Thread.sleep(10)
        }
    }

    private fun settings(): FeedSettingsViewModel {
        val model = FeedSettingsViewModel(SavedStateHandle(mapOf("feedId" to "f1")), repository, session)
        until { !model.isLoading.value && model.feedInfo.value != null }
        return model
    }

    private fun added(model: FeedSettingsViewModel) {
        model.addSource("https://example.org/feed.xml", "rss")
        until { !model.isAddingSource.value && (model.pendingPermission.value != null || model.addSourceError.value != null) }
    }

    @Test
    fun `a source's permission the server names is asked for by its name`() {
        val model = settings()
        added(model)
        assertEquals("Fetch from example.org", model.pendingPermission.value?.name)
        assertNull(model.addSourceError.value)
    }

    @Test
    fun `a source's permission the server will not name is reported in the add dialog`() {
        unnamed()
        val model = settings()
        added(model)
        assertNull("THE POINT: no consent opens on a permission the server cannot name", model.pendingPermission.value)
        assertNotNull(model.addSourceError.value)
    }

    @Test
    fun `a source's permission the server names with nothing is reported in the add dialog`() {
        blank()
        val model = settings()
        added(model)
        assertNull("THE POINT: no consent opens on an empty name", model.pendingPermission.value)
        assertNotNull(model.addSourceError.value)
    }

    private fun waited(model: FeedSettingsViewModel) {
        model.allowWaiting("example.org")
        until { !model.addingWaiting.value && (model.waitingPermission.value != null || model.error.value != null) }
    }

    @Test
    fun `a waiting server's permission the server names is asked for by its name`() {
        val model = settings()
        waited(model)
        assertEquals("Fetch from example.org", model.waitingPermission.value?.name)
        assertNull(model.error.value)
    }

    @Test
    fun `a waiting server's permission the server will not name is reported`() {
        unnamed()
        val model = settings()
        waited(model)
        assertNull("THE POINT: no consent opens on a permission the server cannot name", model.waitingPermission.value)
        assertNotNull(model.error.value)
    }

    @Test
    fun `a waiting server's permission the server names with nothing is reported`() {
        blank()
        val model = settings()
        waited(model)
        assertNull("THE POINT: no consent opens on an empty name", model.waitingPermission.value)
        assertNotNull(model.error.value)
    }

    private fun followed(): FindFeedsViewModel {
        val model = FindFeedsViewModel(SavedStateHandle(), repository)
        // Let the screen's own first reads settle, so the error read below is the follow's.
        repeat(20) { shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS); Thread.sleep(10) }
        model.clearError()
        model.follow(Feed(id = "f1", fingerprint = "f1", name = "Feed", owner = 1), "@pat@example.org")
        until { model.following.value == null && (model.pendingPermission.value != null || model.error.value != null) }
        return model
    }

    @Test
    fun `a follow's permission the server names is asked for by its name`() {
        val model = followed()
        assertEquals("Fetch from example.org", model.pendingPermission.value?.name)
        assertNull(model.error.value)
    }

    @Test
    fun `a follow's permission the server will not name is reported`() {
        unnamed()
        val model = followed()
        assertNull("THE POINT: no consent opens on a permission the server cannot name", model.pendingPermission.value)
        assertNotNull(model.error.value)
    }

    @Test
    fun `a follow's permission the server names with nothing is reported`() {
        blank()
        val model = followed()
        assertNull("THE POINT: no consent opens on an empty name", model.pendingPermission.value)
        assertNotNull(model.error.value)
    }
}
