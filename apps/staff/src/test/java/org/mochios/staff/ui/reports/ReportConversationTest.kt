// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.reports

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.AppContext
import org.mochios.staff.api.StaffApi
import org.mochios.staff.model.Report
import org.mochios.staff.repository.StaffRepository
import org.mochios.staff.ws.StaffEventsBus
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * A report filed from a conversation shows it on request: only that report's
 * dialog holds it, and a report with no conversation asks for nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReportConversationTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private val reads = CopyOnWriteArrayList<String>()
    @Volatile private var refuse = false
    @Volatile private var slow = false

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringAfter("/staff/")
                return when {
                    path.startsWith("-/reports/list") -> ok("""{"reports": [], "total": 0}""")
                    path.startsWith("-/threads/get") -> {
                        reads += path
                        if (refuse) {
                            MockResponse().setResponseCode(502).setBody("""{"error": "Comptroller is not available"}""")
                        } else {
                            val answer = ok(
                                """{"thread": {"id": "t1"}, "messages": [
                                   {"id": "m1", "sender": "b1", "sender_name": "Buyer", "body": "Can you get me one?", "created": 100}]}""",
                            )
                            if (slow) answer.setBodyDelay(300, TimeUnit.MILLISECONDS) else answer
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

    private fun model(): ReportsViewModel {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/staff/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val model = ReportsViewModel(
            StaffRepository(retrofit.create(StaffApi::class.java)),
            StaffEventsBus(),
            SavedStateHandle(),
        )
        settle { !model.state.value.isLoading }
        return model
    }

    private fun settle(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    private val reported = Report(id = "r1", type = "user", target = "b1", thread = "t1", status = "pending")
    private val plain = Report(id = "r2", type = "user", target = "b2", status = "pending")

    @Test
    fun `a report filed from a conversation loads it on request`() {
        val model = model()
        model.openAction(reported)
        assertNull(model.state.value.conversation)
        assertEquals(0, reads.size)
        model.showConversation()
        settle { model.state.value.conversation != null }
        val conversation = model.state.value.conversation!!
        assertEquals("Can you get me one?", conversation.single().body)
        assertEquals("Buyer", conversation.single().senderName)
        assertEquals(listOf("-/threads/get?id=t1"), reads.toList())
        assertFalse(model.state.value.loadingConversation)
    }

    @Test
    fun `another report's dialog does not keep the conversation`() {
        val model = model()
        model.openAction(reported)
        model.showConversation()
        settle { model.state.value.conversation != null }
        model.openAction(plain)
        assertNull(model.state.value.conversation)
        model.showConversation()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, reads.size)
        assertNull(model.state.value.conversation)
    }

    @Test
    fun `a conversation that arrives after another report opens is not shown there`() {
        slow = true
        val model = model()
        model.openAction(reported)
        model.showConversation()
        model.openAction(plain)
        settle { reads.isNotEmpty() }
        Thread.sleep(600)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("r2", model.state.value.actionDialog?.id)
        assertNull(model.state.value.conversation)
    }

    @Test
    fun `a conversation that cannot be read leaves the button usable`() {
        refuse = true
        val model = model()
        model.openAction(reported)
        model.showConversation()
        settle { !model.state.value.loadingConversation && reads.isNotEmpty() }
        assertNull(model.state.value.conversation)
        refuse = false
        model.showConversation()
        settle { model.state.value.conversation != null }
        assertEquals(2, reads.size)
    }
}
