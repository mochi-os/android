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
import org.mochios.people.repository.PeopleRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val ADA = """{"id": "c1", "book": "b1", "name": "Ada", "etag": "e1", "card": [{"name": "FN", "params": {}, "value": "Ada"}]}"""

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
                    } else {
                        ok("""{"contact": $ADA}""")
                    }
                    "-/contacts/create" -> ok("""{"contact": $ADA}""")
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

    @Test
    fun `a copy is created from the contact, with the edits typed before it, and the contact is left as it is`() {
        val model = ContactEditViewModel(SavedStateHandle(mapOf("id" to "c1")), repository)
        until { model.uiState.value.contact != null && model.uiState.value.books.isNotEmpty() }
        model.updateForm(model.uiState.value.form.copy(name = "Ada Lovelace"))
        model.copy()
        assertTrue(model.uiState.value.copying)
        model.save()
        until { model.uiState.value.saved }
        val body = sent("create").single()
        assertTrue(body, body.contains("\"source\":\"c1\""))
        assertTrue(body, body.contains("Ada Lovelace"))
        assertTrue(body, body.contains("\"book\":\"b1\""))
        assertEquals(emptyList<String>(), sent("update"))
    }

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the editor's menu offers Copy, which turns it into one for a copy`() {
        val model = ContactEditViewModel(SavedStateHandle(mapOf("id" to "c1")), repository)
        rule.setContent {
            ContactEditScreen(onBack = {}, onSaved = {}, onDeleted = {}, viewModel = model)
        }
        until { model.uiState.value.contact != null }
        rule.onNodeWithText("Edit contact").assertExists()
        rule.onNodeWithContentDescription("Contact actions").performClick()
        rule.onNodeWithText("Copy").performClick()
        rule.onNodeWithText("Copy contact").assertExists()
        assertTrue(model.uiState.value.copying)
        rule.onNodeWithContentDescription("Contact actions").assertDoesNotExist()
    }

    @Test
    fun `a copy cannot delete the contact it was made from`() {
        val model = ContactEditViewModel(SavedStateHandle(mapOf("id" to "c1")), repository)
        until { model.uiState.value.contact != null }
        model.copy()
        model.confirmDelete()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(model.uiState.value.isDeleting)
        assertEquals(emptyList<String>(), asked.filter { it.path.orEmpty().endsWith("/-/contacts/delete") }.map { it.path })
    }

    @Test
    fun `a merge reads the survivor holding both cards, and saving writes it and names the contact it absorbs`() {
        val model = ContactEditViewModel(SavedStateHandle(mapOf("id" to "c1")), repository)
        until { model.uiState.value.contact != null && model.uiState.value.books.isNotEmpty() }
        model.merge("c2")
        until { model.uiState.value.merge != null }
        val preview = sent("get").last()
        assertTrue(preview, preview.contains("contact=c1") && preview.contains("source=c2"))
        assertEquals("Ada Byron", model.uiState.value.form.name)
        model.save()
        until { model.uiState.value.saved }
        val body = sent("update").single()
        assertTrue(body, body.contains("\"contact\":\"c2\""))
        assertTrue(body, body.contains("\"etag\":\"e2\""))
        assertTrue(body, body.contains("\"source\":{\"id\":\"c1\",\"etag\":\"e1\"}"))
        assertTrue(body, body.contains("byron@example.com"))
        assertEquals(emptyList<String>(), sent("create"))
    }

    @Test
    fun `the merge list offers every other contact, in name order`() {
        val model = ContactEditViewModel(SavedStateHandle(mapOf("id" to "c1")), repository)
        until { model.uiState.value.contact != null }
        model.openMerge()
        until { model.uiState.value.mergeCandidates.isNotEmpty() }
        assertEquals(listOf("c2", "c3"), model.uiState.value.mergeCandidates.map { it.id })
    }

    @Test
    fun `a merge refused by the server stays in the list, saying why`() {
        val model = ContactEditViewModel(SavedStateHandle(mapOf("id" to "c1")), repository)
        until { model.uiState.value.contact != null }
        model.openMerge()
        model.merge("c3")
        until { model.uiState.value.mergeError != null }
        val state = model.uiState.value
        assertTrue(state.mergeOpen)
        assertEquals(null, state.merge)
        assertEquals("Ada", state.form.name)
        assertEquals(null, state.mergePending)
    }

    @Test
    fun `a merge cannot delete either contact`() {
        val model = ContactEditViewModel(SavedStateHandle(mapOf("id" to "c1")), repository)
        until { model.uiState.value.contact != null }
        model.merge("c2")
        until { model.uiState.value.merge != null }
        model.confirmDelete()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(model.uiState.value.isDeleting)
        assertEquals(emptyList<String>(), asked.filter { it.path.orEmpty().endsWith("/-/contacts/delete") }.map { it.path })
    }

    @Test
    fun `a merge read from cards since changed is dropped and the contact read again`() {
        val model = ContactEditViewModel(SavedStateHandle(mapOf("id" to "c1")), repository)
        until { model.uiState.value.contact != null }
        model.merge("c2")
        until { model.uiState.value.merge != null }
        stale = true
        model.save()
        until { model.uiState.value.conflict != null }
        assertEquals(null, model.uiState.value.merge)
        until { model.uiState.value.form.name == "Ada" }
    }

    @Test
    fun `the editor's menu offers Merge, whose list turns it into the merge with the contact picked`() {
        val model = ContactEditViewModel(SavedStateHandle(mapOf("id" to "c1")), repository)
        rule.setContent {
            ContactEditScreen(onBack = {}, onSaved = {}, onDeleted = {}, viewModel = model)
        }
        until { model.uiState.value.contact != null }
        rule.onNodeWithContentDescription("Contact actions").performClick()
        rule.onNodeWithText("Merge").performClick()
        until { model.uiState.value.mergeCandidates.isNotEmpty() }
        rule.onNodeWithText("Ada Byron").performClick()
        until { model.uiState.value.merge != null }
        rule.onNodeWithText("Merge contacts").assertExists()
        rule.onNodeWithContentDescription("Contact actions").assertDoesNotExist()
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
}
