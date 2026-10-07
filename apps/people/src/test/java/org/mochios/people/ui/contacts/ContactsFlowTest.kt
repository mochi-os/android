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

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                asked.add(request)
                return when (request.path.orEmpty().substringAfter("/people/").substringBefore("?")) {
                    "-/contacts" -> ok("""{"contacts": []}""")
                    "-/contacts/get", "-/contacts/create", "-/contacts/update" -> ok("""{"contact": $ADA}""")
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
        rule.waitUntil(5_000) { model.uiState.value.contact != null }
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
