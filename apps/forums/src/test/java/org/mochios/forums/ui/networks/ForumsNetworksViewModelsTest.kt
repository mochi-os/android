// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.networks

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.mochios.forums.R
import org.mochios.forums.api.ForumsApi
import org.mochios.forums.repository.ForumsRepository
import org.mochios.forums.repository.SavedRepository
import org.mochios.forums.ui.find.CommunitiesEvent
import org.mochios.forums.ui.find.CommunitiesViewModel
import org.mochios.forums.ui.forum.ForumEvent
import org.mochios.forums.ui.forum.ForumViewModel
import org.mochios.forums.ui.newpost.NewPostViewModel
import org.mochios.forums.ui.post.PostViewModel
import org.mochios.forums.ui.settings.CommunityEvent
import org.mochios.forums.ui.settings.CommunityViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/** The forums view models against a stand-in server, for posts and communities from other networks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ForumsNetworksViewModelsTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var api: ForumsApi
    private lateinit var repository: ForumsRepository
    private lateinit var session: SessionManager
    private lateinit var socket: MochiWebSocket

    private val requests = ConcurrentLinkedQueue<Pair<String, String>>()

    /** Whether the sensitive mark is refused. */
    @Volatile private var refuseSensitive = false

    private val post = """{"id": "p1", "forum": "f1", "title": "Linked", "body": "Text", "member": "https://lemmy.example/u/someone",
        "url": "https://there.example/a", "sensitive": 1, "language": "fr"}"""

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                requests.add(path to request.body.readUtf8())
                return when (path) {
                    "/forums/-/post/create" -> ok("""{"id": "p9", "forum": "f1"}""")
                    "/forums/f1/-/p1" -> ok("""{"forum": {"id": "f1"}, "post": $post, "comments": []}""")
                    "/forums/f1/-/p1/edit" -> ok("""{"success": true}""")
                    "/forums/-/blocks/add" -> ok("{}")
                    "/forums/f1/-/info" -> ok("""{"forum": {"id": "f1", "name": "Books", "account": "l1", "address": "!books@lemmy.example"}, "permissions": {}}""")
                    "/forums/f1/-/posts" -> ok("""{"forum": {"id": "f1", "fingerprint": "fp1", "name": "Books", "account": "l1", "address": "!books@lemmy.example"}, "posts": []}""")
                    "/forums/-/accounts/list" -> raw("""[{"id": "h2", "type": "fediverse", "identifier": "zed@here.example", "hosted": true},
                        {"id": "h1", "type": "fediverse", "identifier": "amy@here.example", "hosted": true},
                        {"id": "l1", "type": "lemmy", "identifier": "me@lemmy.example", "hosted": false},
                        {"id": "m1", "type": "mastodon", "identifier": "me@social.example", "hosted": false}]""")
                    "/forums/-/communities/join" -> ok("""{"id": "c1", "fingerprint": "cfp", "name": "Books", "already_subscribed": false}""")
                    "/forums/-/communities/add" -> raw("""{"added": 3, "communities": 3, "posts": 9}""")
                    "/forums/-/communities/import" -> raw("""{"communities": 2, "blocks": 1, "saved": 4, "more": true}""")
                    "/forums/f1/-/publish" -> ok("""{"published": false, "domains": ["b.example", "a.example"], "handles": []}""")
                    "/forums/f1/-/publish/set" -> ok("""{"published": true, "address": "https://a.example/c/books", "identifier": "!books@a.example", "domains": []}""")
                    "/forums/f1/-/publish/clear" -> ok("""{"published": false, "domains": ["a.example"]}""")
                    "/forums/f1/-/sensitive/set" ->
                        if (refuseSensitive) MockResponse().setResponseCode(403).setBody("""{"error": "forbidden", "message": "No"}""")
                        else ok("""{"sensitive": 1}""")
                    "/_/websocket" -> MockResponse().setResponseCode(404)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/forums/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ForumsApi::class.java)
        repository = ForumsRepository(api, FileStore(context), context)
        session = SessionManager(context)
        val base = server.url("/").toString().trimEnd('/')
        runBlocking { session.setServerUrl(base) }
        socket = MochiWebSocket(OkHttpClient(), Gson(), object : SocketSession {
            override suspend fun serverUrl(): String = base
            override suspend fun token(app: String): String? = null
            override suspend fun invalidate(app: String) {}
        })
    }

    @After
    fun end() {
        socket.disconnectAll()
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun raw(body: String) = MockResponse().setBody(body)

    private fun until(what: String, done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out waiting for $what; requests ${requests.map { it.first }}" }
            Thread.sleep(10)
        }
    }

    private fun sent(path: String): String? = requests.lastOrNull { it.first == path }?.second

    private fun part(body: String, name: String): String? =
        Regex("name=\"$name\"\\r\\n(?:[^\\r\\n]+\\r\\n)*\\r\\n([^\\r]*)\\r\\n").find(body)?.groupValues?.get(1)

    @Test
    fun `a new post carries its link, its sensitive mark and its language, and an unmarked one none`() {
        val model = NewPostViewModel(SavedStateHandle(mapOf("forumId" to "f1")), context, repository)
        model.setTitle("Title")
        model.setBody("Body")
        model.setLink(" https://there.example/a ")
        model.setSensitive(true)
        model.setLanguage("fr")
        model.submit()
        until("the post") { model.uiState.value.postSuccess }
        val body = sent("/forums/-/post/create")!!
        assertEquals("https://there.example/a", part(body, "url"))
        assertEquals("1", part(body, "sensitive"))
        assertEquals("fr", part(body, "language"))

        requests.clear()
        val plain = NewPostViewModel(SavedStateHandle(mapOf("forumId" to "f1")), context, repository)
        plain.setTitle("Title")
        plain.setBody("Body")
        plain.submit()
        until("the plain post") { plain.uiState.value.postSuccess }
        val second = sent("/forums/-/post/create")!!
        assertNull(part(second, "url"))
        assertNull(part(second, "sensitive"))
        assertNull(part(second, "language"))
    }

    @Test
    fun `a link that is not a web address is not sent`() {
        val model = NewPostViewModel(SavedStateHandle(mapOf("forumId" to "f1")), context, repository)
        model.setTitle("Title")
        model.setBody("Body")
        model.setLink("there.example")
        model.submit()
        repeat(10) { shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS); Thread.sleep(10) }
        assertNull(sent("/forums/-/post/create"))
    }

    @Test
    fun `an edit loads the post's marks and sends each as it stands, so clearing one reaches the server`() {
        val model = NewPostViewModel(SavedStateHandle(mapOf("forumId" to "f1", "postId" to "p1")), context, repository)
        until("the post") { model.link.value.isNotEmpty() }
        assertTrue(model.sensitive.value)
        assertEquals("fr", model.language.value)
        model.setLink("")
        model.setSensitive(false)
        model.setLanguage("")
        model.submit()
        until("the edit") { model.uiState.value.postSuccess }
        val body = sent("/forums/f1/-/p1/edit")!!
        assertEquals("", part(body, "url"))
        assertEquals("0", part(body, "sensitive"))
        assertEquals("", part(body, "language"))
    }

    @Test
    fun `an author from another network is blocked by their page, and their server by its domain`() {
        val model = PostViewModel(SavedStateHandle(mapOf("forumId" to "f1", "postId" to "p1")), context, repository, session, socket)
        until("the post") { model.uiState.value.post.id == "p1" }
        model.block("https://lemmy.example/u/someone")
        until("the block") { model.uiState.value.done == R.string.forums_blocked }
        assertEquals("target=https%3A%2F%2Flemmy.example%2Fu%2Fsomeone", sent("/forums/-/blocks/add"))
    }

    @Test
    fun `a community read through a linked account is blocked by its address, and the screen leaves it`() {
        val model = ForumViewModel(SavedStateHandle(mapOf("forumId" to "f1")), repository, SavedRepository(api, Gson()), socket, session)
        val events = mutableListOf<ForumEvent>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch { model.events.collect { events.add(it) } }
        until("the forum") { model.uiState.value.forum.address.isNotEmpty() }
        model.blockCommunity()
        until("the block") { events.any { it is ForumEvent.Unsubscribed } }
        assertEquals("target=%21books%40lemmy.example", sent("/forums/-/blocks/add"))
        job.cancel()
    }

    @Test
    fun `a community is joined as the first hosted account by address, and a Lemmy account's communities are read in`() {
        val model = CommunitiesViewModel(repository)
        val events = mutableListOf<CommunitiesEvent>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch { model.events.collect { events.add(it) } }
        until("the accounts") { model.hosted.value.isNotEmpty() && model.lemmy.value.isNotEmpty() }
        assertEquals(listOf("h1", "h2"), model.hosted.value.map { it.id })
        assertEquals(listOf("l1"), model.lemmy.value.map { it.id })
        model.join("  !books@lemmy.example ", "")
        until("the join") { events.any { it is CommunitiesEvent.Joined } }
        assertEquals("address=%21books%40lemmy.example&account=h1", sent("/forums/-/communities/join"))
        assertEquals(CommunitiesEvent.Joined("cfp", false), events.first { it is CommunitiesEvent.Joined })
        model.add(model.lemmy.value.first())
        until("the add") { events.any { it is CommunitiesEvent.Added } }
        model.import(model.lemmy.value.first())
        until("the import") { events.any { it is CommunitiesEvent.Imported } }
        assertTrue((events.first { it is CommunitiesEvent.Imported } as CommunitiesEvent.Imported).answer.more)
        assertEquals("account=l1", sent("/forums/-/communities/import"))
        job.cancel()
    }

    @Test
    fun `a forum is published as a community on the domain chosen, and withdrawn`() {
        val model = CommunityViewModel(SavedStateHandle(mapOf("forumId" to "f1")), repository)
        val events = mutableListOf<CommunityEvent>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch { model.events.collect { events.add(it) } }
        model.load(sensitive = false)
        until("the state") { model.state.value != null }
        assertEquals(listOf("a.example", "b.example"), model.domains(model.state.value))
        model.publish(" Books ", "a.example")
        until("the publish") { model.state.value?.published == true }
        assertEquals("forum=f1&name=books&domain=a.example", sent("/forums/f1/-/publish/set"))
        assertTrue(events.contains(CommunityEvent.Done(R.string.forums_community_published)))
        model.withdraw()
        until("the withdrawal") { model.state.value?.published == false }
        job.cancel()
    }

    @Test
    fun `the sensitive mark is saved, and put back when the server refuses it`() {
        val model = CommunityViewModel(SavedStateHandle(mapOf("forumId" to "f1")), repository)
        model.load(sensitive = false)
        model.setSensitive(true)
        until("the mark") { sent("/forums/f1/-/sensitive/set") != null && !model.busy.value }
        assertEquals("forum=f1&sensitive=1", sent("/forums/f1/-/sensitive/set"))
        assertTrue(model.sensitive.value)

        refuseSensitive = true
        requests.clear()
        model.setSensitive(false)
        until("the refusal") { sent("/forums/f1/-/sensitive/set") != null && !model.busy.value }
        assertTrue(model.sensitive.value)
        assertFalse(sent("/forums/f1/-/sensitive/set").isNullOrEmpty())
    }
}
