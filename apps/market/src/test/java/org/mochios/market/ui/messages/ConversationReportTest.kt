// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.messages

import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
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
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.market.R
import org.mochios.market.api.MarketApi
import org.mochios.market.repository.MarketRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Reporting from a conversation over a real view model: the report names the
 * conversation, a filed one closes the dialog, and a refused one keeps it open
 * on the server's reason with the form usable again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConversationReportTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer

    /** The reason `-/reports/create` refuses with, or null to accept. */
    @Volatile private var refusal: String? = null
    private val reports = CopyOnWriteArrayList<String>()

    private val closed = mutableListOf<Unit>()
    private val filed = mutableListOf<Unit>()

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringAfter("/market/").substringBefore("?")
                return when (path) {
                    "-/threads/get" -> ok(
                        """{"thread": {"id": "t1", "listing": "l1", "buyer": "b1", "seller": "s1"},
                           "messages": [{"id": "m1", "thread": "t1", "sender": "b1", "body": "Can you get me one?", "created": 100}],
                           "listing": {"id": "l1", "title": "Hunting knife"}}""",
                    )
                    "-/reports/create" -> {
                        reports += request.body.readUtf8()
                        val reason = refusal
                        if (reason == null) {
                            ok("""{"ok": true}""")
                        } else {
                            MockResponse().setResponseCode(400).setBody("""{"error": "$reason"}""")
                        }
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun end() = server.shutdown()

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun model(): MessageThreadViewModel {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/market/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val api = retrofit.create(MarketApi::class.java)
        val model = MessageThreadViewModel(
            SavedStateHandle(mapOf("listingId" to "l1", "threadId" to "t1")),
            MarketRepository(api, FileStore(context)),
            SessionManager(context),
        )
        settle { model.state.value.thread != null && !model.state.value.isLoading }
        return model
    }

    /** [until] for before any content is set, which the rule's waits need. */
    private fun settle(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    /**
     * Waits for [done], running the main looper the network's answers resume
     * on; a condition that reads the model alone never runs it.
     */
    private fun until(done: () -> Boolean) {
        rule.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            done()
        }
        rule.waitForIdle()
    }

    private fun count(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun string(id: Int) = context.getString(id)

    private fun show(model: MessageThreadViewModel) {
        rule.setContent {
            var open by remember { mutableStateOf(true) }
            ConversationReportHost(
                open = open,
                viewModel = model,
                onClose = {
                    open = false
                    closed += Unit
                },
                onFiled = { filed += Unit },
            )
        }
        rule.waitForIdle()
    }

    private fun submit() {
        rule.onNode(hasText(string(R.string.market_report_dialog_submit)) and hasClickAction()).performClick()
    }

    @Test
    fun `a filed report names the conversation and closes the dialog`() {
        val model = model()
        show(model)
        assertEquals(1, count(string(R.string.market_report_conversation_title)))
        submit()
        until { closed.isNotEmpty() }
        assertEquals(1, reports.size)
        val body = reports[0]
        assertTrue(body, body.contains("thread=t1"))
        assertTrue(body, body.contains("type=user"))
        assertTrue(body, body.contains("reason=prohibited"))
        assertEquals(1, filed.size)
        assertEquals(0, count(string(R.string.market_report_conversation_title)))
    }

    @Test
    fun `the dialog offers the reasons that fit a conversation`() {
        val model = model()
        show(model)
        rule.onNode(hasText(string(R.string.market_report_reason_prohibited))).performClick()
        rule.waitForIdle()
        assertEquals(1, count(string(R.string.market_report_reason_inappropriate)))
        assertEquals(1, count(string(R.string.market_report_reason_spam)))
        assertEquals(0, count(string(R.string.market_report_reason_counterfeit)))
        assertEquals(0, count(string(R.string.market_report_reason_misleading)))
    }

    @Test
    fun `a refused report stays open on the reason and can be sent again`() {
        refusal = "Thread not found"
        val model = model()
        show(model)
        submit()
        until { count("Thread not found") == 1 }
        assertEquals(1, reports.size)
        assertEquals(0, closed.size)
        assertEquals(0, filed.size)
        assertEquals(0, count(string(R.string.market_report_dialog_submitting)))
        refusal = null
        submit()
        until { closed.isNotEmpty() }
        assertEquals(2, reports.size)
        assertEquals(1, filed.size)
    }
}
