// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.contacts

import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import org.mochios.android.api.MochiError
import org.mochios.android.api.userMessage
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
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
import java.util.Collections
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

    /** How many friends' photos the next photos refresh answers as changed; later ones answer none. */
    @Volatile private var changed = 0

    /** Holds the photos refresh's reply until released. */
    @Volatile private var photosHeld: CountDownLatch? = null

    /** The server's refusal of every action on the list, while set. */
    @Volatile private var refusal: String? = null

    /** The list fails to load, while set. */
    @Volatile private var down = false

    /** The list comes back with no contacts in it. */
    @Volatile private var empty = false

    /** Holds the contacts list's reply until released. */
    @Volatile private var listing: CountDownLatch? = null

    /** The status reading a contact fails with, while set. */
    @Volatile private var unreadable: Int? = null

    /**
     * Ada linked to a person, with whether the invitation to them is out, as
     * a server that answers it sends; unset, the server predates the field.
     */
    @Volatile private var invitation: Boolean? = null

    /** The etag a read of Ada answers with, her card the same, while set: the server restamped it. */
    @Volatile private var restamp: String? = null

    /** Every update fails, while set. */
    @Volatile private var failing = false

    /** Holds a contact's read until released. */
    @Volatile private var reading: CountDownLatch? = null

    /** Collectors of a view model's events, stopped at the end. */
    private val listening = mutableListOf<Job>()

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                asked.add(request)
                val body = request.body.clone().readUtf8()
                val path = request.path.orEmpty().substringAfter("/people/").substringBefore("?")
                val reason = refusal
                if (reason != null && path in REFUSABLE) {
                    return MockResponse().setResponseCode(409).setBody("""{"error": "$reason"}""")
                }
                return when (path) {
                    "-/contacts" -> when {
                        listing?.await(10, TimeUnit.SECONDS) == false -> MockResponse().setResponseCode(504)
                        down -> MockResponse().setResponseCode(500)
                            .setBody("""{"error": "The address book could not be read"}""")
                        empty -> ok("""{"contacts": []}""")
                        else -> ok(
                            """{"contacts": [{"id": "c3", "name": "Grace", "person": "p2"}, {"id": "c1", "name": "Ada"}, {"id": "c2", "name": "Ada Byron", "person": "p1"}]}""",
                        )
                    }
                    "-/friends/invite", "-/friends/remove", "-/books/rename", "-/books/delete" -> ok("{}")
                    "-/contacts/get" -> when {
                        reading?.await(10, TimeUnit.SECONDS) == false -> MockResponse().setResponseCode(504)
                        unreadable != null -> MockResponse().setResponseCode(unreadable!!).setBody(
                            if (unreadable == 404) """{"error": "Contact not found"}""" else """{"error": "The contact could not be read"}""",
                        )
                        body.contains("source=c2") -> ok("""{"contact": $MERGED, "source": $ADA}""")
                        body.contains("source=c3") -> MockResponse().setResponseCode(409)
                            .setBody("""{"error": "Both contacts are linked to Mochi people and cannot be merged"}""")
                        else -> {
                            var contact = ADA
                            invitation?.let { out -> contact = contact.replace("\"name\": \"Ada\",", "\"name\": \"Ada\", \"person\": \"p1\", \"invited\": $out,") }
                            restamp?.let { tag -> contact = contact.replace("\"etag\": \"e1\"", "\"etag\": \"$tag\"") }
                            ok("""{"contact": $contact}""")
                        }
                    }
                    "-/contacts/update" -> if (failing) {
                        MockResponse().setResponseCode(500).setBody("""{"error": "Not now"}""")
                    } else if (stale) {
                        MockResponse().setResponseCode(412)
                            .setBody("""{"error": "The contact was changed elsewhere. Reload and try again."}""")
                    } else if (body.contains("\"source\"")) {
                        ok("""{"contact": $MERGED}""")
                    } else {
                        ok("""{"contact": $SAVED}""")
                    }
                    "-/contacts/create" -> ok("""{"contact": $MADE}""")
                    "-/contacts/delete" -> ok("{}")
                    "-/contacts/photos" -> {
                        photosHeld?.await(10, TimeUnit.SECONDS)
                        val answer = changed
                        changed = 0
                        ok("""{"changed": $answer}""")
                    }
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
    fun end() {
        listening.forEach { it.cancel() }
        server.shutdown()
    }

    /** What [model] tells its screen from now on, as it is told. */
    private fun told(model: ContactsViewModel): List<ContactsEvent> {
        val events = Collections.synchronizedList(mutableListOf<ContactsEvent>())
        listening += CoroutineScope(Dispatchers.Unconfined).launch { model.events.collect { events.add(it) } }
        return events
    }

    /** The list, loaded with its address books. */
    private fun loaded(): ContactsViewModel {
        val model = ContactsViewModel(SavedStateHandle(), repository)
        until { model.uiState.value.contacts.isNotEmpty() && model.uiState.value.books.isNotEmpty() }
        return model
    }

    private val grace = Contact(id = "c3", person = "p2", name = "Grace")

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun sent(action: String): List<String> =
        asked.filter { it.path.orEmpty().endsWith("/-/contacts/$action") }.map { it.body.clone().readUtf8() }

    /**
     * Runs the main thread until [done], the server answering on its own.
     * [advance] moves its clock on each turn, so a debounced reload comes due.
     */
    private fun until(advance: Boolean = false, done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            if (advance) {
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            } else {
                shadowOf(Looper.getMainLooper()).idle()
            }
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
    fun `a new contact is made by Create, and the editor goes back to where it was started`() {
        val model = ContactEditViewModel(SavedStateHandle(), repository)
        until { model.uiState.value.books.isNotEmpty() }
        model.updateForm(model.uiState.value.form.copy(name = "Ada Lovelace"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500))
        assertEquals(emptyList<String>(), sent("create"))
        model.create()
        until { model.uiState.value.left }
        assertEquals(null, model.uiState.value.opened)
        val body = sent("create").single()
        assertTrue(body, body.contains("Ada Lovelace"))
        assertFalse(body, body.contains("\"source\""))
    }

    @Test
    fun `Copy saves what is pending, then opens the copy as a new contact, making nothing`() {
        val model = editor()
        model.updateForm(model.uiState.value.form.copy(name = "Ada Lovelace"))
        model.copy()
        until { model.uiState.value.copied == "c1" }
        assertTrue(sent("update").single().contains("Ada Lovelace"))
        assertEquals(emptyList<String>(), sent("create"))
        assertEquals(null, model.uiState.value.opened)
    }

    @Test
    fun `a copy opens on the card it copies, and Create makes it from that card`() {
        val model = ContactEditViewModel(SavedStateHandle(mapOf("copy" to "c1")), repository)
        until { model.uiState.value.form.name == "Ada" && model.uiState.value.books.isNotEmpty() }
        assertEquals("b1", model.uiState.value.form.book)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500))
        assertEquals(emptyList<String>(), sent("create"))
        model.create()
        until { model.uiState.value.left }
        val body = sent("create").single()
        assertTrue(body, body.contains("\"source\":\"c1\""))
        assertTrue(body, body.contains("Ada"))
        assertTrue(body, body.contains("\"book\":\"b1\""))
    }

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `an open contact has no Save, and its menu's Copy opens the copy`() {
        val model = editor()
        val copies = mutableListOf<String>()
        rule.setContent {
            ContactEditScreen(onBack = {}, onOpen = {}, onCopy = { copies += it }, onDeleted = {}, viewModel = model)
        }
        // Titled with the contact's name rather than "Edit contact".
        rule.onNodeWithText("Edit contact").assertDoesNotExist()
        rule.onNodeWithContentDescription("Save").assertDoesNotExist()
        rule.onNodeWithContentDescription("Contact actions").performClick()
        rule.onNodeWithText("Copy").performClick()
        until { model.uiState.value.copied == "c1" }
        rule.waitForIdle()
        assertEquals(listOf("c1"), copies)
        assertEquals(emptyList<String>(), sent("create"))
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

    private fun listed() = asked.filter { it.path.orEmpty().substringBefore("?").endsWith("/-/contacts") }

    private fun photos() = asked.filter { it.path.orEmpty().endsWith("/-/contacts/photos") }

    /** Lets a photos request the server has answered finish on the client, so the next listing may ask again. */
    private fun settle() {
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()
    }

    // A friend's photo is a fetch from their server, five seconds when it is
    // offline: no listing asks for them, and every listing is followed by one
    // request that does, the screen not waiting for it.
    @Test
    fun `every listing asks for no photos and is followed by one request for them`() {
        val model = ContactsViewModel(SavedStateHandle(), repository)
        until { model.uiState.value.contacts.isNotEmpty() && photos().size == 1 }
        assertEquals("POST", photos().single().method)
        settle()
        model.refresh()
        until { listed().size == 2 && photos().size == 2 }
        assertTrue(listed().joinToString { it.path.orEmpty() }, listed().all { it.path.orEmpty().contains("photos=0") })
    }

    @Test
    fun `a changed photo reloads the list, and none changed leaves it alone`() {
        changed = 1
        ContactsViewModel(SavedStateHandle(), repository)
        until(advance = true) { listed().size == 2 && photos().size == 2 }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        Thread.sleep(100)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, listed().size)
    }

    @Test
    fun `one request for photos at a time, however many listings meanwhile`() {
        val held = CountDownLatch(1)
        photosHeld = held
        val model = ContactsViewModel(SavedStateHandle(), repository)
        until { photos().size == 1 }
        model.refresh()
        until { listed().size == 2 && !model.uiState.value.isRefreshing }
        assertEquals(1, photos().size)
        held.countDown()
        photosHeld = null
        settle()
        model.refresh()
        until { listed().size == 3 && photos().size == 2 }
    }

    /** Lets the main thread's clock run past any debounced reload, the server answering. */
    private fun pass() {
        repeat(10) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            Thread.sleep(20)
        }
    }

    private fun reads() =
        asked.filter { request -> request.path.orEmpty().endsWith("/-/contacts/get") }

    @Test
    fun `a save of the editor's own does not read the card it was answered with again`() {
        val model = editor()
        pass()
        val before = reads().size
        val listings = listed().size
        model.updateForm(model.uiState.value.form.copy(name = "Ada Lovelace"))
        model.save()
        until { sent("update").isNotEmpty() && !model.uiState.value.isSaving }
        pass()
        assertEquals(before, reads().size)
        assertEquals(listings, listed().size)
    }

    @Test
    fun `a deleted contact is not read again`() {
        val model = editor()
        pass()
        val before = reads().size
        model.confirmDelete()
        until { model.uiState.value.deleted }
        pass()
        assertEquals(before, reads().size)
    }

    @Test
    fun `lists asked for together share one request`() {
        val held = CountDownLatch(1)
        books = held
        val first = ContactsViewModel(SavedStateHandle(), repository)
        val second = ContactsViewModel(SavedStateHandle(), repository)
        held.countDown()
        until {
            first.uiState.value.contacts.isNotEmpty() && second.uiState.value.contacts.isNotEmpty()
        }
        assertEquals(1, listed().size)
    }

    // The list shows no sign of an invitation, and an action that fails
    // leaves the list as it was, so the screen says each.
    @Test
    fun `an invitation sent from the list says so`() {
        val model = loaded()
        val events = told(model)
        model.invite(grace)
        until { events.isNotEmpty() }
        assertEquals(listOf<ContactsEvent>(ContactsEvent.Invited), events.toList())
        assertEquals(1, asked.count { it.path.orEmpty().endsWith("/-/friends/invite") })
    }

    @Test
    fun `an invitation the server refuses says why, and the list stays`() {
        AppContext.set(context)
        val model = loaded()
        val events = told(model)
        refusal = "You have already invited this person"
        model.invite(grace)
        until { events.isNotEmpty() }
        val failed = events.single() as ContactsEvent.Failed
        assertEquals("You have already invited this person", failed.error.userMessage())
        assertEquals(null, model.uiState.value.error)
        assertEquals(3, model.uiState.value.contacts.size)
        assertFalse(model.uiState.value.isMutating)
    }

    @Test
    fun `every action on the list that fails says why`() {
        AppContext.set(context)
        val model = loaded()
        val events = told(model)
        val book = model.uiState.value.books.first()
        refusal = "Refused"
        model.requestUnfriend(grace)
        model.confirmUnfriend()
        until { events.size == 1 }
        model.requestDelete(grace)
        model.confirmDelete()
        until { events.size == 2 }
        model.requestRenameBook(book)
        model.confirmRenameBook("Work")
        until { events.size == 3 }
        model.requestDeleteBook(book)
        model.confirmDeleteBook()
        until { events.size == 4 }
        assertEquals(List(4) { "Refused" }, events.map { (it as ContactsEvent.Failed).error.userMessage() })
        assertEquals(null, model.uiState.value.error)
        assertEquals(3, model.uiState.value.contacts.size)
        assertFalse(model.uiState.value.isMutating)
    }

    @Test
    fun `a refresh that fails over a list already showing says why, and the list stays`() {
        AppContext.set(context)
        val model = loaded()
        val events = told(model)
        down = true
        model.refresh()
        until { events.isNotEmpty() }
        val failed = events.single() as ContactsEvent.Failed
        assertEquals("The address book could not be read", failed.error.userMessage())
        assertEquals(null, model.uiState.value.error)
        assertEquals(3, model.uiState.value.contacts.size)
        assertFalse(model.uiState.value.isRefreshing)
    }

    @Test
    fun `a refresh that fails over an empty list shows the error in its place`() {
        empty = true
        val model = ContactsViewModel(SavedStateHandle(), repository)
        until { listed().isNotEmpty() && !model.uiState.value.isLoading }
        val events = told(model)
        down = true
        model.refresh()
        until { model.uiState.value.error != null }
        assertTrue(events.isEmpty())
    }

    @Test
    fun `the screen says an invitation went`() {
        val events = MutableSharedFlow<ContactsEvent>(extraBufferCapacity = 8)
        rule.setContent {
            val host = remember { SnackbarHostState() }
            Box {
                SnackbarHost(host)
                ContactsSnackbars(events, host)
            }
        }
        rule.waitForIdle()
        events.tryEmit(ContactsEvent.Invited)
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Invitation sent").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `the screen says why an action failed, in the server's own words`() {
        AppContext.set(context)
        val events = MutableSharedFlow<ContactsEvent>(extraBufferCapacity = 8)
        rule.setContent {
            val host = remember { SnackbarHostState() }
            Box {
                SnackbarHost(host)
                ContactsSnackbars(events, host)
            }
        }
        rule.waitForIdle()
        events.tryEmit(ContactsEvent.Failed(MochiError.Unknown("You have already invited this person")))
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("You have already invited this person").fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ---- leaving, a failed read, and what the editor reads ----

    /** An editor on a contact whose read has failed, once it has. */
    private fun unread(status: Int): ContactEditViewModel {
        unreadable = status
        val model = ContactEditViewModel(SavedStateHandle(mapOf("id" to "c1")), repository)
        until { model.uiState.value.error != null && !model.uiState.value.isLoading }
        return model
    }

    @Test
    fun `an editor whose contact could not be read is left at once`() {
        val model = unread(500)
        model.leave()
        // THE POINT: the failed read does not keep the screen.
        until { model.uiState.value.left }
    }

    @Test
    fun `a failed read is read again by Retry`() {
        val model = unread(500)
        unreadable = null
        model.load()
        until { model.uiState.value.contact != null }
        assertEquals(null, model.uiState.value.error)
        assertEquals("Ada", model.uiState.value.form.name)
    }

    @Test
    fun `a failed read shows why with Retry, not a form that could never be saved`() {
        AppContext.set(context)
        val model = unread(500)
        rule.setContent { ContactEditScreen(onBack = {}, onDeleted = {}, viewModel = model) }
        rule.onNodeWithText("The contact could not be read").assertExists()
        // THE POINT: nothing to type into, and no menu acting on a contact never read.
        assertTrue(rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithContentDescription("Contact actions").assertDoesNotExist()
        unreadable = null
        rule.onNodeWithText("Retry").performClick()
        until { model.uiState.value.contact != null }
        rule.waitForIdle()
        assertTrue(rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `a contact that is gone is not found, with the way back`() {
        AppContext.set(context)
        val model = unread(404)
        var backs = 0
        rule.setContent { ContactEditScreen(onBack = { backs++ }, onDeleted = {}, viewModel = model) }
        rule.onNodeWithText("Contact not found").assertExists()
        rule.onNodeWithText("Retry").assertDoesNotExist()
        rule.onNodeWithText("Back").performClick()
        until { model.uiState.value.left }
        rule.waitForIdle()
        assertEquals(1, backs)
    }

    @Test
    fun `a save that fails on the way out keeps the editor, offering to leave without it`() {
        AppContext.set(context)
        val model = editor()
        failing = true
        model.updateForm(model.uiState.value.form.copy(name = "Ada Lovelace"))
        model.leave()
        until { model.uiState.value.unsaved != null }
        assertFalse(model.uiState.value.left)
        assertEquals("Not now", model.uiState.value.unsaved?.userMessage())
        model.discard()
        assertTrue(model.uiState.value.left)
        // A field losing its focus on the way out saves nothing more.
        model.save()
        pass()
        assertEquals(1, sent("update").size)
    }

    @Test
    fun `the screen offers to discard the edit leaving could not save`() {
        AppContext.set(context)
        val model = editor()
        failing = true
        var backs = 0
        rule.setContent { ContactEditScreen(onBack = { backs++ }, onDeleted = {}, viewModel = model) }
        model.updateForm(model.uiState.value.form.copy(name = "Ada Lovelace"))
        model.leave()
        until { model.uiState.value.unsaved != null }
        rule.waitForIdle()
        // Said in the dialog, and above the form as any failed save is.
        assertTrue(rule.onAllNodesWithText("Not now").fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithText("Discard changes").performClick()
        until { model.uiState.value.left }
        rule.waitForIdle()
        assertEquals(1, backs)
    }

    @Test
    fun `Copy waits on a save that fails, rather than copy the card without the edit`() {
        val model = editor()
        failing = true
        model.updateForm(model.uiState.value.form.copy(name = "Ada Lovelace"))
        model.copy()
        until { model.uiState.value.error != null && !model.uiState.value.isSaving }
        pass()
        assertEquals(null, model.uiState.value.copied)
    }

    @Test
    fun `a merge waits on a save that fails, saying why in its list`() {
        AppContext.set(context)
        val model = editor()
        failing = true
        model.openMerge()
        model.updateForm(model.uiState.value.form.copy(name = "Ada Lovelace"))
        model.merge("c2")
        until { model.uiState.value.mergeError != null }
        assertEquals("Not now", model.uiState.value.mergeError?.userMessage())
        assertTrue(sent("get").none { it.contains("source=c2") })
        assertEquals(null, model.uiState.value.opened)
    }

    @Test
    fun `a copy's form shows once the card it copies is read, so nothing typed is written over`() {
        val held = CountDownLatch(1)
        reading = held
        val model = ContactEditViewModel(SavedStateHandle(mapOf("copy" to "c1")), repository)
        rule.setContent { ContactEditScreen(onBack = {}, onDeleted = {}, viewModel = model) }
        until { reads().isNotEmpty() }
        rule.waitForIdle()
        // THE POINT: no form to type into while the card is on its way.
        assertTrue(rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty())
        held.countDown()
        until { model.uiState.value.form.name == "Ada" && !model.uiState.value.isLoading }
        rule.waitForIdle()
        assertTrue(rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `an older server's list is read before the form shows, so typing is never written over`() {
        val held = CountDownLatch(1)
        listing = held
        val model = ContactEditViewModel(SavedStateHandle(mapOf("id" to "c1")), repository)
        until { listed().isNotEmpty() }
        // THE POINT: no form to type into while the read that follows it is out.
        assertEquals(null, model.uiState.value.contact)
        held.countDown()
        until { model.uiState.value.contact != null && !model.uiState.value.isLoading }
        model.updateForm(model.uiState.value.form.copy(name = "Ada Lovelace"))
        pass()
        assertEquals("Ada Lovelace", model.uiState.value.form.name)
    }

    @Test
    fun `the editor learns the invitation from the contact, listing no contacts`() {
        invitation = true
        val model = editor()
        pass()
        assertEquals(setOf("p1"), model.uiState.value.sent)
        // THE POINT: no list of every contact is read for one contact's switch.
        assertTrue(listed().isEmpty())
        val before = reads().size
        runBlocking { repository.removeFriend("p9") }
        until(advance = true) { reads().size > before }
        pass()
        // Nor when a change elsewhere has the contact read again.
        assertTrue(listed().isEmpty())
    }

    @Test
    fun `a server that predates the field is asked for the list`() {
        val model = editor()
        until { listed().isNotEmpty() }
        assertEquals(emptySet<String>(), model.uiState.value.sent)
    }

    @Test
    fun `a card the server only restamped takes its new etag while an edit is typed`() {
        invitation = false
        val model = editor()
        pass()
        model.updateForm(model.uiState.value.form.copy(name = "Ada Lovelace"))
        restamp = "e7"
        val before = reads().size
        // A change signal, as a friend's photo arriving sends: the card is read again.
        runBlocking { repository.removeFriend("p9") }
        until(advance = true) { reads().size > before }
        until { model.uiState.value.contact?.etag == "e7" }
        model.save()
        until { sent("update").isNotEmpty() }
        val body = sent("update").single()
        // THE POINT: the save goes against the etag the card now has, not refused as changed elsewhere.
        assertTrue(body, body.contains("\"etag\":\"e7\""))
        assertTrue(body, body.contains("Ada Lovelace"))
    }
}

/** The actions on the list a refusal answers. */
private val REFUSABLE = setOf("-/friends/invite", "-/friends/remove", "-/contacts/delete", "-/books/rename", "-/books/delete")
