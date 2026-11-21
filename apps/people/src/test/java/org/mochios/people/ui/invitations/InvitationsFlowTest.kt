// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.invitations

import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.android.api.userMessage
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.people.R
import org.mochios.people.api.PeopleApi
import org.mochios.people.repository.PeopleRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * The invitations against a server. An action is taken on a list that is
 * showing, so a failure is said in the snackbar and the list stays; only a
 * first read that fails takes the lists' place. A refused policy stays in
 * its dialog, saying why.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InvitationsFlowTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var repository: PeopleRepository

    /** The server's refusal of every action, while set. */
    @Volatile private var refusal: String? = null

    /** The lists fail to load, while set. */
    @Volatile private var down = false

    private val listening = mutableListOf<Job>()

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringAfter("/people/").substringBefore("?")
                val reason = refusal
                return when (path) {
                    "-/contacts" -> if (down) {
                        MockResponse().setResponseCode(500).setBody("""{"error": "The invitations could not be read"}""")
                    } else {
                        ok(
                            """{"contacts": [], "received": [{"id": "p1", "name": "Ada", "direction": "from"}],
                                "sent": [{"id": "p2", "name": "Grace", "direction": "to"}]}""",
                        )
                    }
                    "-/contacts/photos" -> ok("""{"changed": 0}""")
                    "-/preferences/get" -> ok("""{"policy": "notify"}""")
                    "-/friends/accept", "-/friends/ignore", "-/friends/remove", "-/preferences/set" ->
                        if (reason != null) MockResponse().setResponseCode(409).setBody("""{"error": "$reason"}""") else ok("{}")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/people/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        repository = PeopleRepository(retrofit.create(PeopleApi::class.java), FileStore(context))
    }

    @After
    fun end() {
        listening.forEach { it.cancel() }
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    /** The screen's model, its lists read, and what it tells the screen from now on. */
    private fun loaded(): Pair<InvitationsViewModel, List<InvitationsEvent>> {
        val model = InvitationsViewModel(repository)
        until { model.uiState.value.received.isNotEmpty() && !model.uiState.value.isLoading }
        val events = Collections.synchronizedList(mutableListOf<InvitationsEvent>())
        listening += CoroutineScope(Dispatchers.Unconfined).launch { model.events.collect { events.add(it) } }
        return model to events
    }

    @Test
    fun `every action on an invitation that fails says why, and the lists stay`() {
        val (model, events) = loaded()
        val received = model.uiState.value.received.single()
        val sent = model.uiState.value.sent.single()
        refusal = "Invitation not found"
        model.accept(received)
        until { events.size == 1 }
        model.decline(received)
        until { events.size == 2 }
        model.cancel(sent)
        until { events.size == 3 }
        // THE POINT: each refusal reaches the screen, in the server's words.
        assertEquals(List(3) { "Invitation not found" }, events.map { (it as InvitationsEvent.Failed).error.userMessage() })
        assertNull(model.uiState.value.error)
        assertEquals(1, model.uiState.value.received.size)
        assertEquals(1, model.uiState.value.sent.size)
    }

    @Test
    fun `reading the lists again over lists showing says why it failed, and they stay`() {
        val (model, events) = loaded()
        down = true
        model.refresh()
        until { events.isNotEmpty() }
        assertEquals("The invitations could not be read", (events.single() as InvitationsEvent.Failed).error.userMessage())
        assertNull(model.uiState.value.error)
        assertEquals(1, model.uiState.value.received.size)
    }

    @Test
    fun `a first read that fails takes the lists' place`() {
        down = true
        val model = InvitationsViewModel(repository)
        until { model.uiState.value.error != null }
        assertTrue(model.uiState.value.received.isEmpty())
    }

    @Test
    fun `a refused policy stays in its dialog, saying why`() {
        val (model, events) = loaded()
        model.openSettings()
        refusal = "Not now"
        model.setPolicy("reject")
        until { !model.uiState.value.savingPolicy && model.uiState.value.policyFailure != null }
        // THE POINT: the dialog stays open with the reason; the lists are not replaced.
        assertTrue(model.uiState.value.settingsDialogOpen)
        assertEquals("Not now", model.uiState.value.policyFailure?.userMessage())
        assertNull(model.uiState.value.error)
        assertEquals("notify", model.uiState.value.policy)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `the policy dialog says why its save was refused`() {
        rule.setContent {
            InviteSettingsDialog(
                initial = "notify",
                loadError = null,
                failure = MochiError.Unknown("Not now"),
                isSaving = false,
                onDismiss = {},
                onSave = {},
            )
        }
        rule.onNodeWithText("Not now").assertExists()
    }

    @Test
    fun `the screen says why an action failed`() {
        val events = MutableSharedFlow<InvitationsEvent>(extraBufferCapacity = 8)
        rule.setContent {
            val host = remember { SnackbarHostState() }
            Box {
                SnackbarHost(host)
                InvitationsSnackbars(events, host)
            }
        }
        rule.waitForIdle()
        events.tryEmit(InvitationsEvent.Failed(MochiError.Unknown("Invitation not found")))
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Invitation not found").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `a section's heading carries its count in the user's number format`() {
        rule.setContent { StackedSectionHeader(heading = R.string.people_invitations_received_count, count = 1234) }
        rule.onNodeWithText("Received (1,234)").assertExists()
    }
}
