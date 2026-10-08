// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.contacts

import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasSetTextAction
import androidx.lifecycle.SavedStateHandle
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.files.FileStore
import org.mochios.people.api.PeopleApi
import org.mochios.people.model.Contact
import org.mochios.people.repository.PeopleRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val ADA = """{"id": "c1", "book": "b1", "name": "Ada", "etag": "e1", "card": [{"name": "FN", "params": {}, "value": "Ada"}]}"""

// Ada as a save answers: the same contact under a new etag.
private const val SAVED = """{"id": "c1", "book": "b1", "name": "Ada", "etag": "e5", "card": [{"name": "FN", "params": {}, "value": "Ada"}]}"""

// A contact a create or a copy made.
private const val MADE = """{"id": "c9", "book": "b1", "name": "Ada Lovelace", "etag": "e9", "card": [{"name": "FN", "params": {}, "value": "Ada Lovelace"}]}"""

// The card a merge of Ada into a contact linked to a Mochi person reads as:
// that contact survives, holding both cards' details.
private const val MERGED = """{"id": "c2", "book": "b1", "person": "p1", "name": "Ada Byron", "etag": "e2", "card": [{"name": "FN", "params": {}, "value": "Ada Byron"}, {"name": "EMAIL", "params": {}, "value": "byron@example.com"}]}"""

/** The contact list against a server: a reply that lands late leaves what changed meanwhile alone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ContactsFlowTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var repository: PeopleRepository
    private val asked = ConcurrentLinkedQueue<RecordedRequest>()

    /** Holds the address books' reply until released. */
    @Volatile private var books: CountDownLatch? = null

    /** Answers every update as changed elsewhere. */
    @Volatile private var stale = false

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                asked.add(request)
                val body = request.body.clone().readUtf8()
                return when (request.path.orEmpty().substringAfter("/people/").substringBefore("?")) {
                    "-/contacts" -> ok(
                        """{"contacts": [{"id": "c3", "name": "Grace", "person": "p2"}, {"id": "c1", "name": "Ada"}, {"id": "c2", "name": "Ada Byron", "person": "p1"}]}""",
                    )
                    "-/contacts/get" -> when {
                        body.contains("source=c2") -> ok("""{"contact": $MERGED, "source": $ADA}""")
                        body.contains("source=c3") -> MockResponse().setResponseCode(409)
                            .setBody("""{"error": "Both contacts are linked to Mochi people and cannot be merged"}""")
                        else -> ok("""{"contact": $ADA}""")
                    }
                    "-/contacts/update" -> if (stale) {
                        MockResponse().setResponseCode(412)
                            .setBody("""{"error": "The contact was changed elsewhere. Reload and try again."}""")
                    } else if (body.contains("\"source\"")) {
                        ok("""{"contact": $MERGED}""")
                    } else {
                        ok("""{"contact": $SAVED}""")
                    }
                    "-/contacts/create" -> ok("""{"contact": $MADE}""")
                    "-/books" -> {
                        books?.await(10, TimeUnit.SECONDS)
                        ok("""{"books": [{"id": "b1", "name": "Home"}]}""")
                    }
                    "-/welcome" -> ok("""{"seen": true}""")
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
    fun end() = server.shutdown()

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun sent(action: String): List<String> =
        asked.filter { it.path.orEmpty().endsWith("/-/contacts/$action") }.map { it.body.clone().readUtf8() }

    /** Runs the main thread until [done], the server answering on its own. */
    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    /** An open contact's editor, read. */
    private fun editor(): ContactEditViewModel {
        val model = ContactEditViewModel(SavedStateHandle(mapOf("id" to "c1")), repository)
        until { model.uiState.value.contact != null && model.uiState.value.books.isNotEmpty() }
        return model
    }

    @Test
    fun `a change saves once typing rests for a second, against the etag it read`() {
        val model = editor()
        model.updateForm(model.uiState.value.form.copy(name = "Ada Lovelace"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(900))
        assertEquals(emptyList<String>(), sent("update"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        until { sent("update").isNotEmpty() }
        val body = sent("update").single()
        assertTrue(body, body.contains("\"etag\":\"e1\""))
        assertTrue(body, body.contains("Ada Lovelace"))
    }

    @Test
    fun `leaving a field saves it at once, and the next save sends the etag the last came back with`() {
        val model = editor()
        model.updateForm(model.uiState.value.form.copy(name = "Ada Lovelace"))
        model.save()
        until { sent("update").size == 1 && !model.uiState.value.isSaving }
        model.updateForm(model.uiState.value.form.copy(name = "Ada King"))
        model.save()
        until { sent("update").size == 2 }
        assertTrue(sent("update")[1], sent("update")[1].contains("\"etag\":\"e5\""))
    }

    @Test
    fun `nothing is saved when nothing changed, nor for a contact left with no name`() {
        val model = editor()
        model.save()
        model.updateForm(model.uiState.value.form.copy(name = " "))
        model.save()
        // Saves go one at a time, so the one after them shows whether they sent.
        model.updateForm(model.uiState.value.form.copy(name = "Ada King"))
        model.save()
        until { sent("update").isNotEmpty() && !model.uiState.value.isSaving }
        val updates = sent("update")
        assertEquals(1, updates.size)
        assertTrue(updates.single(), updates.single().contains("Ada King"))
    }

    @Test
    fun `leaving the screen saves what is pending, then lets it go`() {
        val model = editor()
        model.updateForm(model.uiState.value.form.copy(name = "Ada Lovelace"))
        model.leave()
        until { model.uiState.value.left }
        assertEquals(1, sent("update").size)
    }

    @Test
    fun `a save refused as changed elsewhere says so and reads the contact again`() {
        val model = editor()
        stale = true
        model.updateForm(model.uiState.value.form.copy(name = "Ada Lovelace"))
        model.save()
        until { model.uiState.value.conflict != null }
        until { model.uiState.value.form.name == "Ada" }
    }

    @Test
    fun `a new contact is made by Create, and the editor goes on as it`() {
        val model = ContactEditViewModel(SavedStateHandle(), repository)
        until { model.uiState.value.books.isNotEmpty() }
        model.updateForm(model.uiState.value.form.copy(name = "Ada Lovelace"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500))
        assertEquals(emptyList<String>(), sent("create"))
        model.create()
        until { model.uiState.value.opened == "c9" }
        val body = sent("create").single()
        assertTrue(body, body.contains("Ada Lovelace"))
        assertFalse(body, body.contains("\"source\""))
    }

    @Test
    fun `a copy is made at once from the card as it stands, and opened`() {
        val model = editor()
        model.updateForm(model.uiState.value.form.copy(name = "Ada Lovelace"))
        model.copy()
        until { model.uiState.value.opened == "c9" }
        val body = sent("create").single()
        assertTrue(body, body.contains("\"source\":\"c1\""))
        assertTrue(body, body.contains("Ada Lovelace"))
    }

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `an open contact has no Save, and its menu's Copy opens the copy`() {
        val model = editor()
        val opened = mutableListOf<String>()
        rule.setContent {
            ContactEditScreen(onBack = {}, onOpen = { opened += it }, onDeleted = {}, viewModel = model)
        }
        // Titled with the contact's name rather than "Edit contact".
        rule.onNodeWithText("Edit contact").assertDoesNotExist()
        rule.onNodeWithContentDescription("Save").assertDoesNotExist()
        rule.onNodeWithContentDescription("Contact actions").performClick()
        rule.onNodeWithText("Copy").performClick()
        until { model.uiState.value.opened == "c9" }
        rule.waitForIdle()
        assertEquals(listOf("c9"), opened)
    }

    @Test
    fun `moving from one field to another saves the one left`() {
        val model = editor()
        rule.setContent {
            ContactEditScreen(onBack = {}, onOpen = {}, onDeleted = {}, viewModel = model)
        }
        val fields = rule.onAllNodes(hasSetTextAction())
        fields[0].performClick()
        fields[0].performTextReplacement("Ada Lovelace")
        fields[1].performClick()
        until { sent("update").isNotEmpty() }
        assertTrue(sent("update").single().contains("Ada Lovelace"))
    }

    @Test
    fun `a merge is made as soon as the contact is picked, the server combining the cards, and the survivor opened`() {
        val model = editor()
        model.merge("c2")
        until { model.uiState.value.opened == "c2" }
        assertTrue(sent("get").any { it.contains("contact=c1") && it.contains("source=c2") })
        val body = sent("update").single()
        assertTrue(body, body.contains("\"contact\":\"c2\""))
        assertTrue(body, body.contains("\"etag\":\"e2\""))
        assertTrue(body, body.contains("\"source\":{\"id\":\"c1\",\"etag\":\"e1\"}"))
        assertFalse(body, body.contains("\"properties\""))
    }

    @Test
    fun `the merge list offers every other contact, in name order`() {
        val model = editor()
        model.openMerge()
        until { model.uiState.value.mergeCandidates.isNotEmpty() }
        assertEquals(listOf("c2", "c3"), model.uiState.value.mergeCandidates.map { it.id })
    }

    @Test
    fun `a merge refused by the server stays in the list, saying why`() {
        val model = editor()
        model.openMerge()
        model.merge("c3")
        until { model.uiState.value.mergeError != null }
        val state = model.uiState.value
        assertTrue(state.mergeOpen)
        assertEquals(null, state.opened)
        assertEquals("Ada", state.form.name)
        assertEquals(null, state.mergePending)
        assertEquals(emptyList<String>(), sent("update"))
    }

    @Test
    fun `a search typed while the address books load stays`() {
        val held = CountDownLatch(1)
        books = held
        val model = ContactsViewModel(SavedStateHandle(), repository)
        until { asked.any { it.path.orEmpty().contains("/-/books") } }
        model.setSearchQuery("ann")
        held.countDown()
        until { model.uiState.value.books.isNotEmpty() }
        assertEquals("ann", model.uiState.value.searchQuery)
    }

    @Test
    fun `a friend's row has no label beside the name, only the message button`() {
        rule.setContent {
            ContactRow(
                contact = Contact(id = "c2", person = "p1", friend = true, name = "Ada Byron"),
                onTap = {},
                onMessage = {},
                onInvite = {},
                onUnfriend = {},
                onDelete = {},
            )
        }
        rule.onNodeWithText("Ada Byron").assertExists()
        rule.onNodeWithText("Friend").assertDoesNotExist()
        rule.onNodeWithContentDescription("Message").assertExists()
    }
}
