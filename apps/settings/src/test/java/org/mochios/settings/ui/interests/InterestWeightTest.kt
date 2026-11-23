// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.interests

import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.AppContext
import org.mochios.settings.api.Interest
import org.mochios.settings.api.InterestsApi
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** An interest's weight is saved once, where the slider is let go, as the web saves it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class InterestWeightTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private val saves = CopyOnWriteArrayList<String>()
    private val lists = AtomicInteger()

    /** Whether the server refuses a weight. */
    @Volatile private var refuse = false

    /** Holds a weight's answer back until released, when set. */
    @Volatile private var saveGate: CountDownLatch? = null

    /** Holds each read of the list after the first back until released, when set. */
    @Volatile private var listGate: CountDownLatch? = null

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setBody(body).setHeader("Content-Type", "application/json")

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path.orEmpty().substringBefore("?").removePrefix("/")) {
                    "settings/-/user/interests" -> {
                        if (lists.incrementAndGet() > 1) listGate?.await(5, TimeUnit.SECONDS)
                        json("""{"interests": [{"qid": "Q1", "label": "Cats", "weight": 50}, {"qid": "Q2", "label": "Dogs", "weight": 20}], "summary": ""}""")
                    }
                    "settings/-/user/interests/set" -> {
                        saves += request.body.readUtf8()
                        saveGate?.await(5, TimeUnit.SECONDS)
                        if (refuse) json("""{"error": "Not now"}""", 500) else json("{}")
                    }
                    "settings/-/user/interests/remove" -> json("{}")
                    "settings/-/user/interests/search" -> json("""{"error": "Too many searches, wait a minute"}""", 429)
                    else -> MockResponse().setResponseCode(404)
                }
        }
        server.start()
    }

    @After
    fun end() {
        saveGate?.countDown()
        listGate?.countDown()
        server.shutdown()
    }

    private fun model(): InterestsViewModel {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(InterestsApi::class.java)
        val made = InterestsViewModel(api)
        until { !made.uiState.value.isLoading && made.uiState.value.interests.isNotEmpty() }
        return made
    }

    private val slider = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    /** A drag used to send a save for every tick it passed, each in its own request. */
    @Test
    fun `a drag saves once, when the slider is let go, at a whole tick`() {
        val weights = mutableListOf<Int>()
        rule.setContent {
            InterestRow(interest = Interest(qid = "Q1", label = "Cats", weight = -100), onWeightChange = { weights += it }, onRemove = {})
        }
        for (fraction in listOf(0.35f, 0.65f, 0.85f)) {
            rule.onNode(slider).performTouchInput {
                swipe(start = Offset(width * 0.05f, centerY), end = Offset(width * fraction, centerY), durationMillis = 400)
            }
            rule.waitForIdle()
        }
        assertEquals("one save per drag", 3, weights.size)
        assertTrue("each a multiple of ten: $weights", weights.all { it % 10 == 0 })
    }

    /** The position was truncated, so a tick that landed just short of its ten saved 29. */
    @Test
    fun `a slider position is read as the nearest whole weight`() {
        assertEquals(30, sliderWeight(29.999998f))
        assertEquals(-30, sliderWeight(-30.000002f))
        assertEquals(-100, sliderWeight(-99.99999f))
    }

    /** A refusal used to put back the whole list as it was when that step was sent. */
    @Test
    fun `a refused weight puts back that one row's weight, leaving a removal made since`() {
        refuse = true
        saveGate = CountDownLatch(1)
        val model = model()
        model.setWeight("Q1", 80)
        assertEquals(80, model.uiState.value.interests.first { it.qid == "Q1" }.weight)
        listGate = CountDownLatch(1)
        model.remove("Q2")
        saveGate!!.countDown()
        until { model.uiState.value.error != null }
        assertEquals(listOf("Q1"), model.uiState.value.interests.map { it.qid })
        assertEquals(50, model.uiState.value.interests.single().weight)
        listGate!!.countDown()
    }

    @Test
    fun `a weight the row already has is not sent`() {
        val model = model()
        model.setWeight("Q1", 50)
        val settled = System.currentTimeMillis() + 300
        until { System.currentTimeMillis() > settled }
        assertEquals(emptyList<String>(), saves)
    }

    /** A failed search used to say only "Search failed. Please try again.", whatever the reason. */
    @Test
    fun `a failed search says the server's reason`() {
        val model = model()
        rule.setContent { InterestsScreen(onBack = {}, viewModel = model) }
        model.onSearchQueryChange("cats")
        until { model.uiState.value.searchError != null }
        rule.waitForIdle()
        rule.onNode(androidx.compose.ui.test.hasText("Too many searches, wait a minute")).assertExists()
    }

    /** A change used to blank the list to a spinner while it was read again. */
    @Test
    fun `the interests stay while they are read again after a change`() {
        val model = model()
        listGate = CountDownLatch(1)
        model.remove("Q2")
        until { lists.get() >= 2 }
        assertTrue(!model.uiState.value.isLoading)
        assertEquals(listOf("Q1"), model.uiState.value.interests.map { it.qid })
        listGate!!.countDown()
    }
}
