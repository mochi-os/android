// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.forum

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.ui.components.LastViewedStore
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.mochios.forums.api.ForumsApi
import org.mochios.forums.model.Post
import org.mochios.forums.repository.ForumsRepository
import org.mochios.forums.repository.SavedRepository
import org.mochios.forums.ui.forumlist.ForumListViewModel
import org.mochios.forums.ui.post.PostViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * What a forum asks the server for as it opens beside the drawer: one roster
 * read between them, from `-/information` rather than the posts-laden
 * `-/list`, and the first page already in the saved sort. The roster is the
 * signed-in account's alone, and coming back reads it afresh.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ForumRosterTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var session: SessionManager
    private lateinit var api: ForumsApi
    private lateinit var repository: ForumsRepository
    private lateinit var base: String

    /** How many times `-/information` has been asked for. */
    private val informations = AtomicInteger()

    /** How many times `-/list` has been asked for. */
    private val lists = AtomicInteger()

    /** The sort each list of posts was asked for in, "" for none. */
    private val sorts = ConcurrentLinkedQueue<String>()

    /** The forum's own saved sort. */
    @Volatile private var own = ""

    /** The account's default sort, behind any forum's own. */
    @Volatile private var standard = ""

    /** Whose roster the server answers with. */
    @Volatile private var owner = "a1"

    /** Holds `-/information`'s answer until released. */
    @Volatile private var hold: CountDownLatch? = null

    /** How many times the saved posts have been asked for. */
    private val reads = AtomicInteger()

    /** Holds the saved posts' answer until released. */
    @Volatile private var keep: CountDownLatch? = null

    /** The server's end of the forum's websocket, once the client has connected. */
    @Volatile private var live: WebSocket? = null

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                val roster = """"forums": [{"id": "f1", "fingerprint": "fp1", "name": "$owner forum", "sort": "$own"}]"""
                return when (path) {
                    "/forums/-/information" -> {
                        informations.incrementAndGet()
                        hold?.await(10, TimeUnit.SECONDS)
                        ok("""{$roster, "settings": {"sort": "$standard"}}""")
                    }
                    "/forums/-/list" -> {
                        lists.incrementAndGet()
                        sorts.add(request.requestUrl?.queryParameter("sort").orEmpty())
                        ok("""{$roster, "posts": [{"id": "p1", "forum": "f1"}], "settings": {"sort": "$standard"}}""")
                    }
                    "/forums/f1/-/p1" -> ok(
                        """{"forum": {"id": "f1", "fingerprint": "fp1", "name": "Forum"}, "post": {"id": "p1", "forum": "f1"}, "comments": []}""",
                    )
                    "/forums/f1/-/posts" -> {
                        sorts.add(request.requestUrl?.queryParameter("sort").orEmpty())
                        ok("""{"forum": {"id": "f1", "fingerprint": "fp1", "name": "Forum", "sort": "$own"}, "posts": [{"id": "p1"}]}""")
                    }
                    "/forums/-/saved/list" -> {
                        reads.incrementAndGet()
                        keep?.await(10, TimeUnit.SECONDS)
                        ok("""{"saved": [{"post": {"id": "s1"}, "created": 1}]}""")
                    }
                    "/_/websocket" -> MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            live = webSocket
                        }

                        // Answer the client's close, or the server waits on it.
                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            webSocket.close(code, reason)
                        }
                    })
                    in WRITES -> ok("{}")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        base = server.url("/").toString().trimEnd('/')
        api = Retrofit.Builder()
            .baseUrl(server.url("/forums/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ForumsApi::class.java)
        session = SessionManager(context)
        runBlocking {
            session.setServerUrl(base)
            session.setBoundAccount("a1", base)
        }
        socket = MochiWebSocket(OkHttpClient(), Gson(), object : SocketSession {
            override suspend fun serverUrl(): String = base

            override suspend fun token(app: String): String? = "token"

            override suspend fun invalidate(app: String) {
            }
        })
        repository = ForumsRepository(api, FileStore(context), context, session)
    }

    @After
    fun end() {
        hold?.countDown()
        keep?.countDown()
        socket.disconnectAll()
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    /** The forum screen for [forum], over the same repository as the drawer. */
    private fun forum(forum: String) = ForumViewModel(
        SavedStateHandle(mapOf("forumId" to forum)),
        repository,
        SavedRepository(api, Gson(), session),
        socket,
        session,
    )

    /**
     * Runs the main thread until [done], the server answering on its own. Its
     * clock is moved on each turn, or a debounced reload would never come due.
     */
    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    /** Lets everything already under way finish. */
    private fun settle() {
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
    }

    /** Opens the drawer and [forum] beside it, the forum asking while the
     *  drawer's roster read is still under way, and waits for both. */
    private fun open(forum: String): Pair<ForumListViewModel, ForumViewModel> {
        val held = CountDownLatch(1)
        hold = held
        val drawer = ForumListViewModel(repository)
        until { informations.get() == 1 }
        val model = forum(forum)
        settle()
        held.countDown()
        until { model.uiState.value.posts.isNotEmpty() && drawer.uiState.value.forums.isNotEmpty() }
        settle()
        return drawer to model
    }

    @Test
    fun `a forum opens beside the drawer on one roster read, in the account's default from the first page`() {
        standard = "top"
        val (drawer, model) = open("f1")
        assertEquals(1, informations.get())
        assertEquals(0, lists.get())
        assertEquals(listOf("a1 forum"), drawer.uiState.value.forums.map { it.name })
        assertEquals(listOf("top"), sorts.toList())
        assertEquals("top", model.uiState.value.sort)
    }

    @Test
    fun `a forum's own saved sort comes from the roster, so its first page is already in it`() {
        own = "top"
        val (_, model) = open("f1")
        assertEquals(1, informations.get())
        assertEquals(listOf("top"), sorts.toList())
        assertEquals("top", model.uiState.value.sort)
    }

    @Test
    fun `every forum at once opens beside the drawer on one roster read and one list, in the saved default`() {
        standard = "top"
        val (_, model) = open(LastViewedStore.ALL)
        assertEquals(1, informations.get())
        assertEquals(1, lists.get())
        assertEquals(listOf("top"), sorts.toList())
        assertEquals("top", model.uiState.value.sort)
    }

    @Test
    fun `coming back reads the roster afresh, so a default saved on another device lists the forum in it`() {
        val model = forum("f1")
        until { model.uiState.value.posts.isNotEmpty() && !model.uiState.value.isLoading }
        // The screen's first resume lands behind the load and is not honoured.
        model.reloadOnForeground()
        assertEquals("new", model.uiState.value.sort)
        standard = "top"
        val before = informations.get()
        model.reloadOnForeground()
        until { model.uiState.value.sort == "top" && !model.uiState.value.isLoading }
        settle()
        assertEquals(before + 1, informations.get())
        assertEquals("top", sorts.last())
    }

    @Test
    fun `the roster is held for the signed-in account alone`() = runBlocking {
        repository.forumsInformation()
        assertEquals("a1 forum", repository.forumsInformation().forums.single().name)
        assertEquals(1, informations.get())
        // Somebody else signs in: the first account's roster is not theirs.
        owner = "b2"
        session.setBoundAccount("b2", base)
        assertEquals("b2 forum", repository.forumsInformation().forums.single().name)
        assertEquals(2, informations.get())
        // Signed out, nobody's roster is held.
        session.clearAll()
        repository.forumsInformation()
        repository.forumsInformation()
        assertEquals(4, informations.get())
    }

    @Test
    fun `the saved posts are emptied when another account signs in, and on signing out`() {
        val saved = SavedRepository(api, Gson(), session)
        runBlocking { saved.load() }
        assertEquals(setOf("s1"), saved.savedIds.value)
        runBlocking { session.setBoundAccount("b2", base) }
        until { saved.savedIds.value.isEmpty() && saved.saved.value.isEmpty() }
        runBlocking { saved.load() }
        assertEquals(setOf("s1"), saved.savedIds.value)
        runBlocking { session.clearAll() }
        until { saved.savedIds.value.isEmpty() && saved.saved.value.isEmpty() }
    }

    @Test
    fun `saved posts read for one account and answered after another signs in are not shown`() {
        val saved = SavedRepository(api, Gson(), session)
        runBlocking { saved.load() }
        val held = CountDownLatch(1)
        keep = held
        val reading = Thread { runBlocking { saved.load() } }
        reading.start()
        until { reads.get() == 2 }
        runBlocking { session.setBoundAccount("b2", base) }
        until { saved.savedIds.value.isEmpty() }
        held.countDown()
        reading.join(5_000)
        assertEquals(emptySet<String>(), saved.savedIds.value)
        assertEquals(emptyList<Any>(), saved.saved.value)
    }

    /** Runs [write], with the roster held before it, and checks the next read goes to the server. */
    private suspend fun drops(name: String, write: suspend () -> Unit) {
        repository.forumsInformation()
        val before = informations.get()
        repository.forumsInformation()
        assertEquals("held before $name", before, informations.get())
        write()
        repository.forumsInformation()
        assertEquals("read afresh after $name", before + 1, informations.get())
    }

    @Test
    fun `every write that changes the roster or a sort drops the held one`() = runBlocking {
        drops("create") { repository.createForum("New") }
        drops("subscribe") { repository.subscribe("f2") }
        drops("unsubscribe") { repository.unsubscribe("f1") }
        drops("delete") { repository.deleteForum("f1") }
        drops("rename") { repository.renameForum("f1", "Renamed") }
        drops("forum sort") { repository.setForumSort("f1", "top") }
        drops("default sort") { repository.setDefaultSort("top") }
        drops("join") { repository.joinCommunity("group@example.org", "account") }
        drops("add communities") { repository.addCommunities("account") }
        drops("import communities") { repository.importCommunities("account") }
        drops("block") { repository.addBlock("f1") }
    }

    /** Sends a [type] frame over the forum's socket, the roster held, and waits
     *  for the next read of it to go to the server. */
    private fun dropsOn(type: String) {
        runBlocking { repository.forumsInformation() }
        val before = informations.get()
        runBlocking { repository.forumsInformation() }
        assertEquals("held before $type", before, informations.get())
        live!!.send("""{"type": "$type", "forum": "f1"}""")
        until {
            runBlocking { repository.forumsInformation() }
            informations.get() == before + 1
        }
    }

    @Test
    fun `a forum renamed, left or deleted while open drops the held roster`() {
        val model = forum("f1")
        until { model.uiState.value.posts.isNotEmpty() && live != null }
        settle()
        dropsOn("forum/update")
        dropsOn("forum/removed")
        dropsOn("forum/deleted")
    }

    @Test
    fun `a forum renamed, left or deleted while one of its posts is open drops the held roster`() {
        val model = PostViewModel(SavedStateHandle(mapOf("forumId" to "f1", "postId" to "p1")), context, repository, session, socket)
        until { model.uiState.value.post.id == "p1" && live != null }
        settle()
        dropsOn("forum/update")
        dropsOn("forum/removed")
        dropsOn("forum/deleted")
    }

    @Test
    fun `a roster read as the account changes is not held for the account it began with`() {
        val held = CountDownLatch(1)
        hold = held
        val reading = Thread { runBlocking { repository.forumsInformation() } }
        reading.start()
        until { informations.get() == 1 }
        runBlocking { session.setBoundAccount("b2", base) }
        held.countDown()
        reading.join(5_000)
        runBlocking { session.setBoundAccount("a1", base) }
        runBlocking { repository.forumsInformation() }
        assertEquals(2, informations.get())
    }

    @Test
    fun `no saved posts are read while no identity is known`() {
        runBlocking { session.clearAll() }
        val saved = SavedRepository(api, Gson(), session)
        runBlocking { saved.load() }
        assertEquals(emptySet<String>(), saved.savedIds.value)
        runBlocking { session.setBoundAccount("c3", base) }
        runBlocking { saved.load() }
        assertEquals(setOf("s1"), saved.savedIds.value)
    }

    @Test
    fun `a post saved with no identity known is not carried to the account that signs in next`() {
        runBlocking { session.clearAll() }
        val saved = SavedRepository(api, Gson(), session)
        settle()
        runBlocking { saved.add(Post(id = "x", forum = "f1")) }
        assertEquals(setOf("x"), saved.savedIds.value)
        runBlocking { session.setBoundAccount("c3", base) }
        until { saved.savedIds.value.isEmpty() }
    }

    private companion object {
        /** The writes that change the roster or a sort, each answered as done. */
        val WRITES = setOf(
            "/forums/-/create", "/forums/f2/-/subscribe", "/forums/f1/-/unsubscribe", "/forums/f1/-/delete",
            "/forums/f1/-/rename", "/forums/f1/-/sort/set", "/forums/-/sort/set", "/forums/-/communities/join",
            "/forums/-/communities/add", "/forums/-/communities/import", "/forums/-/blocks/add",
            "/forums/-/saved/add",
        )
    }
}
