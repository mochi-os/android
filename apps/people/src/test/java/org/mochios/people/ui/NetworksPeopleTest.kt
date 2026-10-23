// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui

import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.files.FileStore
import org.mochios.people.api.HandleLookup
import org.mochios.people.api.NetworkPerson
import org.mochios.people.api.PeopleApi
import org.mochios.people.model.Contact
import org.mochios.people.model.ContactMessage
import org.mochios.people.model.ProfileAccount
import org.mochios.people.model.ProfileHistory
import org.mochios.people.model.XmppContact
import org.mochios.people.repository.PeopleRepository
import org.mochios.people.ui.contacts.AddContactViewModel
import org.mochios.people.ui.contacts.ContactEditViewModel
import org.mochios.people.ui.contacts.ContactFields
import org.mochios.people.ui.contacts.ContactRow
import org.mochios.people.ui.contacts.ContactsEvent
import org.mochios.people.ui.contacts.ContactsViewModel
import org.mochios.people.ui.contacts.HandleEntry
import org.mochios.people.ui.contacts.MessagingEntry
import org.mochios.people.ui.contacts.NETWORK_MATRIX
import org.mochios.people.ui.contacts.NetworkRow
import org.mochios.people.ui.contacts.XmppSwitch
import org.mochios.people.ui.contacts.contactForm
import org.mochios.people.model.PersonInformation
import org.mochios.people.ui.contacts.AddContactUiState
import org.mochios.people.ui.contacts.AddContactScreen
import org.mochios.people.ui.contacts.ContactEditScreen
import org.mochios.people.ui.contacts.SearchBody
import org.mochios.people.ui.person.FriendState
import org.mochios.people.ui.person.PersonBody
import org.mochios.people.ui.person.ProfileNetworks
import org.mochios.people.ui.profile.ProfilePreviewDialog
import org.mochios.people.ui.profile.ProfileUiState
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URLDecoder
import java.util.concurrent.ConcurrentLinkedQueue

// Ada as her card is stored: a handle, an XMPP address, and her standing on
// XMPP through the user's account.
private const val ADA = """{"id": "c1", "book": "b1", "name": "Ada", "etag": "e1", "card": [
    {"name": "FN", "params": {}, "value": "Ada"},
    {"name": "SOCIALPROFILE", "params": {"SERVICE-TYPE": ["Fediverse"]}, "value": "@ada@example.social"},
    {"name": "IMPP", "params": {}, "value": "xmpp:ada@example.org"}],
    "xmpp": [{"account": "a1", "identifier": "me@example.org", "address": "ada@example.org", "subscription": "none", "ask": false, "pending": false}]}"""

private const val MADE = """{"id": "c9", "book": "b1", "name": "Bea", "etag": "e9", "card": [{"name": "FN", "params": {}, "value": "Bea"}]}"""

/** People's network work against a stand-in server, and the screens that show it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NetworksPeopleTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var repository: PeopleRepository
    private val asked = ConcurrentLinkedQueue<RecordedRequest>()

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                asked.add(request)
                return when (request.path.orEmpty().substringAfter("/people/").substringBefore("?")) {
                    "-/contacts" -> ok("""{"contacts": [], "received": [], "sent": []}""")
                    "-/contacts/get" -> ok("""{"contact": $ADA}""")
                    "-/contacts/update" -> ok("""{"contact": $ADA}""")
                    "-/contacts/create" -> ok("""{"contact": $MADE}""")
                    "-/contacts/search" -> ok(
                        """{"results": [], "networks": [
                            {"id": "@bea:example.org", "name": "Bea", "address": "matrix:u/bea:example.org", "network": "matrix"},
                            {"id": "@cy:example.org", "name": "Cy", "address": "matrix:u/cy:example.org", "network": "matrix", "contact": "c5"}]}""",
                    )
                    "-/contacts/handle/lookup" -> ok("""{"name": "Ada Lovelace", "address": "@ada@example.social", "avatar": ""}""")
                    "-/friends/invite", "-/friends/remove" -> ok("{}")
                    "-/books" -> ok("""{"books": [{"id": "b1", "name": "Home", "default": true}]}""")
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

    private fun requests(action: String): List<RecordedRequest> =
        asked.filter { it.path.orEmpty().substringBefore("?").endsWith("/-/$action") }

    private fun bodies(action: String): List<String> = requests(action).map { it.body.clone().readUtf8() }

    /** A form body's fields. */
    private fun fields(body: String): Map<String, String> = body.split('&').filter { '=' in it }.associate {
        URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
    }

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    private fun editor(): ContactEditViewModel {
        val model = ContactEditViewModel(SavedStateHandle(mapOf("id" to "c1")), repository)
        until { model.uiState.value.contact != null && model.uiState.value.books.isNotEmpty() }
        return model
    }

    // ---- view models ----

    @Test
    fun `an edit names the handles and messaging addresses it manages, so removing the last one deletes it`() {
        val model = editor()
        model.updateForm(model.uiState.value.form.copy(messaging = emptyList()))
        model.save()
        until { bodies("contacts/update").isNotEmpty() }
        val sent = JSONObject(bodies("contacts/update").single())
        val managed = sent.getJSONArray("managed").let { array -> (0 until array.length()).map { array.getString(it) } }
        assertTrue(managed.containsAll(listOf("SOCIALPROFILE", "IMPP")))
        val properties = sent.getJSONArray("properties")
        val names = (0 until properties.length()).map { properties.getJSONObject(it).getString("name") }
        assertFalse("IMPP" in names)
        assertTrue("SOCIALPROFILE" in names)
    }

    @Test
    fun `a new contact names what it manages`() {
        val model = ContactEditViewModel(SavedStateHandle(), repository)
        until { model.uiState.value.books.isNotEmpty() }
        model.updateForm(model.uiState.value.form.copy(name = "Bea"))
        model.create()
        until { bodies("contacts/create").isNotEmpty() }
        assertTrue(JSONObject(bodies("contacts/create").single()).getJSONArray("managed").toString().contains("IMPP"))
    }

    @Test
    fun `an XMPP switch invites the address through its account, and removes it there`() {
        val model = editor()
        val entry = model.uiState.value.contact!!.xmpp.single()
        model.toggleXmpp(entry, true)
        until { bodies("friends/invite").isNotEmpty() && !model.uiState.value.isToggling }
        val invite = fields(bodies("friends/invite").single())
        assertEquals("xmpp:ada@example.org", invite["person"])
        assertEquals("a1", invite["account"])
        assertEquals("Ada", invite["name"])

        model.toggleXmpp(entry, false)
        until { bodies("friends/remove").isNotEmpty() && !model.uiState.value.isToggling }
        val remove = fields(bodies("friends/remove").single())
        assertEquals("xmpp:ada@example.org", remove["person"])
        assertEquals("a1", remove["account"])
    }

    @Test
    fun `a handle is asked of its network once, and a short one not at all`() {
        val model = editor()
        // Typed with a space after it, as a keyboard leaves one.
        model.lookup("@ada@example.social ")
        until { model.uiState.value.handles.isNotEmpty() }
        model.lookup("@ada@example.social")
        model.lookup("ab")
        model.lookup("two words")
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)
        shadowOf(Looper.getMainLooper()).idle()
        val lookups = requests("contacts/handle/lookup")
        assertEquals(1, lookups.size)
        assertEquals("@ada@example.social", lookups.single().requestUrl?.queryParameter("handle"))
        assertEquals("Ada Lovelace", model.uiState.value.handles["@ada@example.social"]?.name)
    }

    @Test
    fun `someone a search finds on another network is added as a card with their address`() {
        val model = AddContactViewModel(SavedStateHandle(), repository)
        model.updateSearchQuery("bea")
        model.search()
        until { model.uiState.value.networkResults.isNotEmpty() }
        val (bea, cy) = model.uiState.value.networkResults
        assertFalse(model.inContacts(bea))
        assertTrue(model.inContacts(cy))

        model.addNetworkPerson(cy)
        model.addNetworkPerson(bea)
        until { model.inContacts(bea) }
        val made = JSONObject(bodies("contacts/create").single()).getJSONArray("properties")
        assertEquals("Bea", made.getJSONObject(0).getString("value"))
        assertEquals("IMPP", made.getJSONObject(1).getString("name"))
        assertEquals("matrix:u/bea:example.org", made.getJSONObject(1).getString("value"))
    }

    @Test
    fun `Message on a contact with a reachable address opens chat at it`() {
        val model = ContactsViewModel(SavedStateHandle(), repository)
        val got = mutableListOf<ContactsEvent>()
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch { model.events.collect { got.add(it) } }
        shadowOf(Looper.getMainLooper()).idle()
        model.messageContact(Contact(id = "c2", name = "Bea", message = ContactMessage("xmpp:bea@example.org", "a1")))
        until { got.isNotEmpty() }
        assertEquals(
            ContactsEvent.Message("chat/new?account=a1&address=xmpp%3Abea%40example.org&name=Bea"),
            got.single(),
        )
        scope.cancel()
    }

    // ---- screens ----

    @Test
    fun `a handle shows what its network says of the person, and a messaging row its network`() {
        val form = contactForm(null).copy(
            name = "Ada",
            handles = listOf(HandleEntry(value = "@ada@example.social")),
            messaging = listOf(MessagingEntry(value = "@ada:example.org", network = NETWORK_MATRIX)),
        )
        rule.setContent {
            androidx.compose.foundation.layout.Column {
                ContactFields(
                    form = form,
                    books = emptyList(),
                    onChange = {},
                    lookups = mapOf("@ada@example.social" to HandleLookup(name = "Ada Lovelace", address = "@ada@example.social")),
                )
            }
        }
        rule.onNodeWithText("Ada Lovelace · @ada@example.social").assertExists()
        rule.onNodeWithText("Matrix").assertExists()
        rule.onNodeWithText("Fediverse").assertExists()
    }

    @Test
    fun `adding a handle and a messaging address each makes a row`() {
        var changed: org.mochios.people.ui.contacts.ContactForm? = null
        rule.setContent {
            androidx.compose.foundation.layout.Column {
                ContactFields(form = contactForm(null).copy(name = "Ada"), books = emptyList(), onChange = { changed = it })
            }
        }
        rule.onNodeWithText("Add handle").performClick()
        assertEquals(1, changed?.handles?.size)
        rule.onNodeWithText("Add messaging address").performClick()
        assertEquals(1, changed?.messaging?.size)
    }

    @Test
    fun `an XMPP switch says invited, and names the address and account when there are several`() {
        var toggled: Boolean? = null
        rule.setContent {
            XmppSwitch(
                entry = XmppContact(account = "a1", identifier = "me@example.org", address = "ada@example.org", ask = true),
                several = true,
                enabled = true,
                onToggle = { toggled = it },
            )
        }
        rule.onNodeWithText("XMPP contact").assertExists()
        rule.onNodeWithText("Invited").assertExists()
        rule.onNodeWithText("ada@example.org · me@example.org").assertExists()
        rule.onAllNodes(isToggleable()).assertCountEquals(1)
        rule.onAllNodes(isToggleable())[0].performClick()
        assertEquals(false, toggled)
    }

    @Test
    fun `a contact chat can reach offers Message, one it cannot does not`() {
        var reachable by androidx.compose.runtime.mutableStateOf(true)
        rule.setContent {
            ContactRow(
                contact = Contact(
                    id = "c2",
                    name = "Bea",
                    message = if (reachable) ContactMessage("xmpp:bea@example.org", "a1") else null,
                ),
                onTap = {},
                onMessage = {},
                onInvite = {},
                onUnfriend = {},
                onDelete = {},
            )
        }
        rule.onAllNodesWithContentDescription("Message").assertCountEquals(1)
        reachable = false
        rule.onAllNodesWithContentDescription("Message").assertCountEquals(0)
    }

    @Test
    fun `someone on another network is added from the search, or shown as in contacts`() {
        var added = 0
        var inContacts by androidx.compose.runtime.mutableStateOf(false)
        rule.setContent {
            NetworkRow(
                person = NetworkPerson(id = "@bea:example.org", name = "Bea", address = "matrix:u/bea:example.org"),
                added = inContacts,
                busy = false,
                onAdd = { added++ },
            )
        }
        rule.onNodeWithText("@bea:example.org").assertExists()
        rule.onNodeWithText("Add").performClick()
        assertEquals(1, added)
        inContacts = true
        rule.onNodeWithText("In contacts").assertExists()
    }

    @Test
    fun `a profile links its accounts and lists its forum history`() {
        val opened = mutableListOf<String>()
        rule.setContent {
            ProfileNetworks(
                accounts = listOf(
                    ProfileAccount(type = "fediverse", identifier = "@ada@example.social", url = "https://example.social/@ada"),
                    ProfileAccount(type = "bluesky", identifier = "ada.example", url = "http://ada.example"),
                ),
                history = listOf(
                    ProfileHistory(
                        kind = "comment", id = "c1", post = "p9", forum = "f1", forumName = "Engines",
                        fingerprint = "fffffffff", title = "On the analytical engine", body = "It weaves", created = 1_700_000_000,
                    ),
                ),
                onOpenLink = { opened.add(it) },
            )
        }
        rule.onNodeWithText("Fediverse · @ada@example.social").assertExists()
        rule.onNodeWithText("Bluesky · ada.example").assertDoesNotExist()
        rule.onNodeWithText("Forums").assertExists()
        rule.onNodeWithText("It weaves").assertExists()
        rule.onNodeWithText("On the analytical engine").performClick()
        assertEquals(listOf("forums/fffffffff/p9"), opened)
    }

    private val shown = PersonInformation(
        id = "p1",
        name = "Ada",
        accounts = listOf(ProfileAccount(type = "fediverse", identifier = "@ada@example.social", url = "https://example.social/@ada")),
    )

    @Test
    fun `a person's page links the accounts they show`() {
        rule.setContent {
            PersonBody(
                info = shown,
                friendState = FriendState.NotFriend,
                isMutating = false,
                error = null,
                onAddContact = {},
                onInvite = {},
                onAccept = {},
                onDecline = {},
                onMessage = {},
                onOpenLink = {},
            )
        }
        rule.onNodeWithText("Fediverse · @ada@example.social").assertExists()
    }

    @Test
    fun `the preview of the user's own profile links the accounts they show`() {
        rule.setContent {
            ProfilePreviewDialog(state = ProfileUiState(isLoading = false, info = shown, nameDraft = "Ada"), onDismiss = {}, onOpenLink = {})
        }
        rule.onNodeWithText("Fediverse · @ada@example.social").assertExists()
    }

    @Test
    fun `a search that finds only people on other networks lists them`() {
        rule.setContent {
            SearchBody(
                state = AddContactUiState(searchQuery = "bea"),
                stateOf = { org.mochios.people.ui.contacts.AddContactState.NONE },
                networks = listOf(NetworkPerson(id = "@bea:example.org", name = "Bea", address = "matrix:u/bea:example.org")),
                inContacts = { false },
                onAddNetwork = {},
                onQueryChange = {},
                onSearch = {},
                onRetry = {},
                onTapResult = {},
                onAct = {},
                onNewContact = {},
            )
        }
        rule.onNodeWithText("@bea:example.org").assertExists()
        rule.onNodeWithText("No people found").assertDoesNotExist()
    }

    @Test
    fun `the editor shows a contact's XMPP switch and what the network says of their handle`() {
        val model = editor()
        rule.setContent {
            ContactEditScreen(onBack = {}, onOpen = {}, onDeleted = {}, viewModel = model)
        }
        rule.onNodeWithText("XMPP contact").assertExists()
        rule.waitUntil(5_000) {
            rule.onAllNodes(androidx.compose.ui.test.hasText("Ada Lovelace · @ada@example.social")).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1, requests("contacts/handle/lookup").size)
    }

    @Test
    fun `the add screen lists people on other networks, but not while linking a card to a Mochi person`() {
        val plain = AddContactViewModel(SavedStateHandle(), repository)
        val linking = AddContactViewModel(SavedStateHandle(mapOf("contact" to "c1")), repository)
        var showing by androidx.compose.runtime.mutableStateOf(plain)
        rule.setContent {
            AddContactScreen(onBack = {}, onNewContact = {}, onContactsChanged = {}, viewModel = showing)
        }
        plain.updateSearchQuery("bea")
        plain.search()
        until { plain.uiState.value.networkResults.isNotEmpty() }
        rule.onNodeWithText("@bea:example.org").assertExists()

        linking.updateSearchQuery("bea")
        linking.search()
        until { linking.uiState.value.networkResults.isNotEmpty() }
        showing = linking
        rule.onNodeWithText("@bea:example.org").assertDoesNotExist()
    }
}
