// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.ui.newgame

import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.AppContext
import org.mochios.words.api.WordsApi
import org.mochios.words.repository.WordsRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A friends list that failed to load offers Retry, as chess and go do, rather
 * than an error and a disabled Start button until the screen is opened again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FriendsRetryTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var server: MockWebServer
    private lateinit var repository: WordsRepository
    private val reads = AtomicInteger()

    @Before
    fun begin() {
        // The error line reads its message through the application context.
        AppContext.set(RuntimeEnvironment.getApplication())
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path.orEmpty().startsWith("/words/-/new") && reads.incrementAndGet() > 1) {
                    MockResponse().setBody("""{"data": {"friends": [{"id": "f1", "name": "Alice"}]}}""")
                } else {
                    MockResponse().setResponseCode(500)
                }
        }
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/words/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WordsApi::class.java)
        repository = WordsRepository(api)
    }

    @After
    fun end() {
        server.shutdown()
    }

    /** Runs the main looper the network's answers resume on until [done]. */
    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
        rule.waitForIdle()
    }

    @Test
    fun `a failed friends list can be read again`() {
        val model = NewWordsGameViewModel(repository)
        rule.setContent {
            NewWordsGameScreen(onBack = {}, onCreated = {}, onAddFriends = {}, viewModel = model)
        }
        until { model.uiState.value.friendsError != null }
        rule.onNodeWithText("Retry").performClick()
        until { model.uiState.value.friends.isNotEmpty() }
        // Picking the friend counts the seats with the plural, the player included.
        rule.onNodeWithText("Select").performClick()
        rule.onNodeWithText("Alice").performClick()
        rule.onNodeWithText("2 players").assertExists()
    }
}
