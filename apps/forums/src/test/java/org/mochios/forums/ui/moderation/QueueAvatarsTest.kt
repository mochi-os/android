// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.moderation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.forums.api.ForumsApi
import org.mochios.forums.repository.ForumsRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * The moderation queue against a server, every image the screen asks for
 * recorded: an author's avatar route names one of their rows, so a queue that
 * built each row's own address fetched the same picture once per row.
 */
@OptIn(DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h4000dp")
class QueueAvatarsTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var repository: ForumsRepository
    private val loaded = ConcurrentLinkedQueue<String>()

    @Before
    fun begin() {
        // Six posts and six comments, all waiting, by two people in turn.
        val authors = listOf("ann", "bob")
        val posts = (0 until 6).joinToString(",") {
            """{"id": "p$it", "forum": "f1", "member": "${authors[it % 2]}", "name": "${authors[it % 2]}",
                "title": "Post $it", "body": "", "status": "pending"}"""
        }
        val comments = (0 until 6).joinToString(",") {
            """{"id": "c$it", "forum": "f1", "post": "p0", "member": "${authors[it % 2]}",
                "name": "${authors[it % 2]}", "body": "Comment $it", "status": "pending"}"""
        }
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path.orEmpty().substringAfter("/forums/").substringBefore("?")) {
                    "f1/-/moderation/queue" -> MockResponse().setBody(
                        """{"data": {"posts": [$posts], "comments": [$comments], "reports": []}}"""
                    )
                    else -> MockResponse().setResponseCode(404)
                }
        }
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/forums/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        repository = ForumsRepository(retrofit.create(ForumsApi::class.java), FileStore(context), context, SessionManager(context))
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context).components {
                add(Interceptor { chain ->
                    loaded.add(chain.request.data.toString())
                    ErrorResult(null, chain.request, IllegalStateException("no images in tests"))
                })
            }.build()
        )
    }

    @After
    fun end() {
        server.shutdown()
        SingletonImageLoader.reset()
    }

    @Test
    fun `the queue asks for each author's avatar once`() {
        val model = ModerationViewModel(SavedStateHandle(mapOf("forumId" to "f1")), repository)
        rule.setContent { ForumModerationScreen(onBack = {}, viewModel = model) }
        rule.waitUntil(5_000) { model.uiState.value.queue != null }
        rule.waitUntil(5_000) { loaded.isNotEmpty() }
        rule.waitForIdle()
        assertEquals(
            listOf("/forums/f1/-/p0/asset/avatar", "/forums/f1/-/p1/asset/avatar"),
            loaded.toSet().sorted(),
        )
    }
}
