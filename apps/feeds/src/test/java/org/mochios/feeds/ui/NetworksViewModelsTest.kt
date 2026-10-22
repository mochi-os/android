// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui

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
import org.mochios.feeds.api.FeedsApi
import org.mochios.feeds.api.MenuApi
import org.mochios.feeds.repository.FeedsRepository
import org.mochios.feeds.repository.SavedRepository
import org.mochios.feeds.ui.feed.FeedViewModel
import org.mochios.feeds.ui.find.FindFeedsViewModel
import org.mochios.feeds.ui.post.CreatePostViewModel
import org.mochios.feeds.ui.post.PostDetailViewModel
import org.mochios.feeds.ui.settings.FeedSettingsViewModel
import org.mochios.feeds.ui.settings.PublishEvent
import org.mochios.feeds.ui.settings.PublishingSection
import org.mochios.feeds.ui.settings.PublishingViewModel
import org.mochios.feeds.ui.settings.SourcesTab
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/**
 * The feeds view models against a stand-in server: what each sends for posts
 * from and on other networks, and what it makes of the answers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NetworksViewModelsTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var repository: FeedsRepository
    private lateinit var session: SessionManager
    private lateinit var socket: MochiWebSocket

    /** Each request the server took: its path, without the query, and its body. */
    private val requests = ConcurrentLinkedQueue<Pair<String, String>>()

    /** What a post from another network looks like, with a poll and a fediverse copy. */
    private val post = """{"id": "p1", "feed": "f1", "feed_fingerprint": "f1", "body": "Hello",
        "remote": "https://there.example/1", "author": "https://there.example/u/a",
        "copies": [{"address": "@friend@there.example", "network": "fediverse", "remote": "x", "url": "https://there.example/x"}],
        "data": {"poll": {"options": [{"name": "Tea", "votes": 1}, {"name": "Coffee", "votes": 0}], "multiple": false, "expires": 0, "voters": 1}}}"""

    /** The posts the feed lists. */
    @Volatile private var listing = "[$post]"

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                requests.add(path to request.body.readUtf8())
                return when (path) {
                    "/feeds/-/info" -> ok("""{"feeds": [{"id": "f1", "fingerprint": "f1", "name": "Mine", "owner": 1, "privacy": "public"}, {"id": "f2", "fingerprint": "f2", "name": "Theirs", "owner": 0}]}""")
                    "/feeds/f1/-/info" -> ok("""{"feed": {"id": "f1", "fingerprint": "f1", "name": "Mine", "privacy": "public"}, "permissions": {"view": true, "react": true, "comment": true, "manage": true}}""")
                    "/feeds/f1/-/posts", "/feeds/-/posts" -> ok("""{"posts": $listing}""")
                    "/feeds/f1/-/p1" -> ok("""{"posts": [$post], "permissions": {"view": true, "react": true, "comment": true, "manage": true}}""")
                    "/feeds/f1/-/p1/tags" -> ok("""{"tags": []}""")
                    "/feeds/f1/-/post/new" -> ok("""{"feeds": [{"id": "f1", "fingerprint": "f1", "name": "Mine", "privacy": "public"}]}""")
                    "/feeds/-/accounts/list" -> raw("""[{"id": "h2", "type": "fediverse", "identifier": "@me2@here.example", "hosted": true},
                        {"id": "h1", "type": "fediverse", "identifier": "@me@here.example", "hosted": true},
                        {"id": "l1", "type": "mastodon", "identifier": "@me@social.example", "hosted": false},
                        {"id": "b1", "type": "bluesky", "identifier": "me.bsky.social", "hosted": false}]""")
                    "/feeds/f1/-/p1/vote" -> ok("""{"post": "p1", "poll": {"options": [{"name": "Tea", "votes": 1}, {"name": "Coffee", "votes": 1}], "multiple": false, "expires": 0, "voters": 2}, "voted": [1]}""")
                    "/feeds/f1/-/p1/pin" -> ok("""{"post": "p1", "pinned": 1700000000}""")
                    "/feeds/f1/-/p1/unpin" -> ok("""{"post": "p1", "pinned": 0}""")
                    "/feeds/f1/-/p1/boost" -> ok("""{"copies": [{"address": "@me2@here.example", "network": "fediverse", "remote": "y", "url": ""}]}""")
                    "/feeds/f1/-/p1/c1/block", "/feeds/f1/-/p1/c1/mute", "/feeds/f1/-/p1/c1/report" -> ok("""{"done": "x"}""")
                    "/feeds/f1/-/p1/comment/create" -> ok("""{"comment": {"id": "c2"}}""")
                    "/feeds/-/read-all" -> ok("""{"ok": true, "read": 1, "feeds": 2}""")
                    "/feeds/f1/-/post/create" -> ok("""{"id": "p9"}""")
                    "/feeds/f1/-/publish" -> raw("""{"accounts": [{"id": "l1", "type": "mastodon", "identifier": "@me@social.example"},
                        {"id": "b2", "type": "bluesky", "identifier": "me.here.example", "hosted": true}],
                        "publish": ["l1"], "copies": {"l1": 3}, "generators": {}}""")
                    "/feeds/f1/-/publish/set" -> raw("""{"publish": [], "kept": {"l1": 3}}""")
                    "/feeds/f1/-/publish/forget" -> raw("""{"deleted": 3}""")
                    "/feeds/f1/-/generator/set" -> raw("""{"generators": {"b2": "at://did/app.bsky.feed.generator/f1"}}""")
                    "/feeds/f1/-/import" -> raw("""{"imported": 4, "more": true}""")
                    "/feeds/f1/-/filters" -> ok("""{"filters": [{"id": "x", "phrase": "zebra"}, {"id": "y", "phrase": "Apple"}]}""")
                    "/feeds/f1/-/filters/add" -> ok("""{"filters": [{"id": "x", "phrase": "zebra"}, {"id": "y", "phrase": "Apple"}, {"id": "z", "phrase": "mango"}]}""")
                    "/feeds/f1/-/sources" -> ok("""{"sources": []}""")
                    "/feeds/f1/-/sources/add" -> ok("""{"source": {"id": "s1"}, "ingested": 2}""")
                    "/feeds/f1/-/sources/import" -> ok("""{"imported": 5, "more": false, "total": 5}""")
                    "/feeds/-/lookup" -> raw("""{"kind": "person", "network": "fediverse", "id": "https://there.example/u/a", "name": "Alice"}""")
                    "/feeds/-/people" -> ok("""{"people": [{"contact": "c", "name": "Zoe", "handle": "@zoe@there.example", "network": "fediverse"}, {"contact": "d", "name": "Adam", "handle": "@adam@there.example", "network": "fediverse"}]}""")
                    "/feeds/-/recommendations" -> ok("""{"feeds": []}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/feeds/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        repository = FeedsRepository(retrofit.create(FeedsApi::class.java), retrofit.create(MenuApi::class.java), FileStore(context), context)
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

    /** A multipart part's value, or null where the form does not carry it. */
    private fun part(body: String, name: String): String? =
        Regex("name=\"$name\"\\r\\n(?:[^\\r\\n]+\\r\\n)*\\r\\n([^\\r]*)\\r\\n").find(body)?.groupValues?.get(1)

    private fun detail(): PostDetailViewModel {
        val model = PostDetailViewModel(
            SavedStateHandle(mapOf("feedId" to "f1", "postId" to "p1")),
            repository, socket, session, context,
        )
        until("the post") { model.post.value != null }
        return model
    }

    @Test
    fun `a vote sends the options chosen and shows the poll as recounted`() {
        val model = detail()
        model.vote(listOf(1))
        until("the vote") { model.post.value?.voted == listOf(1) }
        assertEquals("feed=f1&post=p1&option=1", sent("/feeds/f1/-/p1/vote"))
        assertEquals(listOf(1, 1), model.post.value?.data?.poll?.options?.map { it.votes })
        assertEquals(2, model.post.value?.data?.poll?.voters)
    }

    @Test
    fun `pinning a post marks it, and unpinning clears the mark`() {
        val model = detail()
        model.togglePin()
        until("the pin") { (model.post.value?.pinned ?: 0) > 0 }
        model.togglePin()
        until("the unpin") { model.post.value?.pinned == 0L }
        assertTrue(requests.any { it.first == "/feeds/f1/-/p1/unpin" })
    }

    @Test
    fun `a boost is made from the first hosted fediverse account, by address, and the copies follow`() {
        val model = detail()
        until("the hosted accounts") { model.hosted.value.isNotEmpty() }
        assertEquals(listOf("h2", "h1"), model.hosted.value.map { it.id })
        model.toggleBoost()
        until("the boost") { model.post.value?.copies?.any { it.address == "@me2@here.example" } == true }
        assertEquals("post=p1&account=h2", sent("/feeds/f1/-/p1/boost"))
    }

    @Test
    fun `the owner mutes, blocks and reports a commenter from another network, a report with its reason`() {
        val model = detail()
        model.moderate("c1", "mute")
        until("the mute") { sent("/feeds/f1/-/p1/c1/mute") != null }
        model.moderate("c1", "block")
        until("the block") { sent("/feeds/f1/-/p1/c1/block") != null }
        model.moderate("c1", "report", "  spam  ")
        until("the report") { sent("/feeds/f1/-/p1/c1/report") != null }
        assertEquals("reason=spam", sent("/feeds/f1/-/p1/c1/report"))
    }

    @Test
    fun `a comment also goes out from the account chosen, and from none when none is`() {
        val model = detail()
        model.setCommentText("Hi")
        model.sendComment()
        until("the first comment") { sent("/feeds/f1/-/p1/comment/create") != null }
        assertNull(part(sent("/feeds/f1/-/p1/comment/create")!!, "account"))
        requests.clear()
        until("the composer") { !model.isSendingComment.value }
        model.setReplyFrom("h1")
        model.setCommentText("Again")
        model.sendComment()
        until("the second comment") { sent("/feeds/f1/-/p1/comment/create") != null }
        assertEquals("h1", part(sent("/feeds/f1/-/p1/comment/create")!!, "account"))
    }

    @Test
    fun `every feed is marked read at once from All feeds`() {
        val model = FeedViewModel(
            SavedStateHandle(mapOf("feedId" to "__all__")),
            repository, SavedRepository(retrofitApi(), Gson()), socket, session, context,
        )
        until("the posts") { model.posts.value.isNotEmpty() }
        model.markAllRead()
        until("the read") { sent("/feeds/-/read-all") != null }
        assertFalse(requests.any { it.first == "/feeds/f1/-/read-all" })
    }

    @Test
    fun `a feed card pins, boosts and votes on its post`() {
        val model = FeedViewModel(SavedStateHandle(mapOf("feedId" to "f1")), repository, SavedRepository(retrofitApi(), Gson()), socket, session, context)
        until("the posts") { model.posts.value.isNotEmpty() }
        model.loadHostedIfNeeded(model.posts.value)
        until("the hosted accounts") { model.hosted.value.isNotEmpty() }
        model.togglePin("f1", "p1")
        until("the pin") { model.posts.value.first().pinned > 0 }
        model.toggleBoost("f1", "p1")
        until("the boost") { model.posts.value.first().copies.any { it.address == "@me2@here.example" } }
        model.vote("f1", "p1", listOf(1))
        until("the vote") { model.posts.value.first().voted == listOf(1) }
        assertEquals("post=p1&account=h2", sent("/feeds/f1/-/p1/boost"))
    }

    @Test
    fun `a new post carries its accounts, features, language and poll`() {
        val model = CreatePostViewModel(SavedStateHandle(mapOf("feedId" to "f1")), repository, context)
        until("the publish list") { model.publish.value != null }
        model.setBody("Hello")
        model.setPublishing("l1", false)
        model.setVisibility("followers")
        model.setWarning("Spoilers")
        model.setSensitive(true)
        model.startPoll()
        model.setPollOption(0, " Tea ")
        model.setPollOption(1, "Coffee")
        model.setPollDuration(3600)
        model.createPost()
        until("the post") { model.postSuccess.value }
        val body = sent("/feeds/f1/-/post/create")!!
        assertEquals("[]", part(body, "accounts"))
        assertEquals("followers", part(body, "visibility"))
        assertEquals("Spoilers", part(body, "warning"))
        assertEquals("true", part(body, "sensitive"))
        assertEquals("en", part(body, "language"))
        assertEquals("""{"options":["Tea","Coffee"],"multiple":false,"duration":3600}""", part(body, "poll"))
    }

    @Test
    fun `a post the writer leaves alone goes where the feed's list says`() {
        val model = CreatePostViewModel(SavedStateHandle(mapOf("feedId" to "f1")), repository, context)
        until("the publish list") { model.publish.value != null }
        model.setBody("Hello")
        model.createPost()
        until("the post") { model.postSuccess.value }
        val body = sent("/feeds/f1/-/post/create")!!
        assertNull(part(body, "accounts"))
        assertNull(part(body, "poll"))
    }

    @Test
    fun `a poll with an empty option is not sent`() {
        val model = CreatePostViewModel(SavedStateHandle(mapOf("feedId" to "f1")), repository, context)
        until("the publish list") { model.publish.value != null }
        model.setBody("Hello")
        model.startPoll()
        model.setPollOption(0, "Tea")
        model.createPost()
        repeat(10) { shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS); Thread.sleep(10) }
        assertNull(sent("/feeds/f1/-/post/create"))
    }

    @Test
    fun `switching an account off offers to delete the copies kept there, and deleting says how many went`() {
        val model = PublishingViewModel(SavedStateHandle(mapOf("feedId" to "f1")), repository, session)
        val events = mutableListOf<PublishEvent>()
        val job = collect(model, events)
        model.loadPublish()
        until("the publish list") { model.publish.value != null }
        val account = model.publish.value!!.accounts.first { it.id == "l1" }
        model.toggle(account, false)
        until("the kept copies") { model.kept.value != null }
        assertEquals("""accounts=%5B%5D""", sent("/feeds/f1/-/publish/set"))
        assertEquals(3, model.kept.value!!.count)
        model.forget(account)
        until("the deletion") { events.any { it is PublishEvent.Deleted } }
        assertEquals(3, (events.first { it is PublishEvent.Deleted } as PublishEvent.Deleted).count)
        assertEquals("account=l1", sent("/feeds/f1/-/publish/forget"))
        job.cancel()
    }

    @Test
    fun `a hosted Bluesky account publishes the feed as a custom feed, and an import says what it brought`() {
        val model = PublishingViewModel(SavedStateHandle(mapOf("feedId" to "f1")), repository, session)
        val events = mutableListOf<PublishEvent>()
        val job = collect(model, events)
        model.loadPublish()
        until("the publish list") { model.publish.value != null }
        val bluesky = model.publish.value!!.accounts.first { it.id == "b2" }
        model.generator(bluesky, true)
        until("the generator") { model.publish.value?.generators?.containsKey("b2") == true }
        assertEquals("account=b2&on=true", sent("/feeds/f1/-/generator/set"))
        model.importFrom(bluesky, "follows")
        until("the import") { events.any { it is PublishEvent.Imported } }
        assertEquals(PublishEvent.Imported("follows", 4, true), events.first { it is PublishEvent.Imported })
        job.cancel()
    }

    @Test
    fun `filters are listed by phrase, and an added one is cut and trimmed`() {
        val model = PublishingViewModel(SavedStateHandle(mapOf("feedId" to "f1")), repository, session)
        model.loadFilters()
        until("the filters") { model.filters.value.isNotEmpty() }
        assertEquals(listOf("Apple", "zebra"), model.filters.value.map { it.phrase })
        model.addFilter("  mango  ")
        until("the added filter") { model.filters.value.size == 3 }
        assertEquals("phrase=mango", sent("/feeds/f1/-/filters/add"))
        assertEquals(listOf("Apple", "mango", "zebra"), model.filters.value.map { it.phrase })
    }

    @Test
    fun `an archive file downloads from its address under the feed, with the feeds token`() {
        val model = PublishingViewModel(SavedStateHandle(mapOf("feedId" to "f1")), repository, session)
        val (url, _) = model.backupFile(org.mochios.feeds.model.PublishAccount(id = "a b"), "2026-10-21.zip")
        assertTrue(url.endsWith("/feeds/f1/-/backups/file?account=a+b&name=2026-10-21.zip"))
    }

    @Test
    fun `a person is followed through the account chosen, and a follow list is imported as text`() {
        val model = FeedSettingsViewModel(SavedStateHandle(mapOf("feedId" to "f1")), repository, session)
        model.addSource("@alice@there.example", "person", "h1")
        until("the source") { sent("/feeds/f1/-/sources/add") != null }
        val added = Gson().fromJson(sent("/feeds/f1/-/sources/add"), Map::class.java)
        assertEquals("person", added["type"])
        assertEquals("@alice@there.example", added["url"])
        assertEquals("h1", added["account"])
        model.importFollows("Account address\nalice@there.example\n")
        until("the import") { model.imported.value != null }
        assertEquals("addresses=Account+address%0Aalice%40there.example%0A", sent("/feeds/f1/-/sources/import"))
        assertEquals(5, model.imported.value!!.imported)
    }

    @Test
    fun `the Find screen looks a handle up elsewhere, follows it into a feed, and lists the people known`() {
        val model = FindFeedsViewModel(SavedStateHandle(), repository)
        until("the people and feeds") { model.people.value.isNotEmpty() && model.owned.value.isNotEmpty() }
        assertEquals(listOf("Adam", "Zoe"), model.people.value.map { it.name })
        assertEquals(listOf("f1"), model.owned.value.map { it.id })
        model.setSearchQuery("cooking")
        repeat(20) { shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS); Thread.sleep(10) }
        assertNull(sent("/feeds/-/lookup"))
        model.setSearchQuery("@alice@there.example")
        until("the lookup") { model.lookup.value != null }
        model.follow(model.owned.value.first(), model.lookup.value!!.id)
        until("the follow") { sent("/feeds/f1/-/sources/add") != null }
        val added = Gson().fromJson(sent("/feeds/f1/-/sources/add"), Map::class.java)
        assertEquals("person", added["type"])
        assertEquals("https://there.example/u/a", added["url"])
    }

    @Test
    fun `the Publish to section switches each account by address, with its copies, and only a hosted Bluesky account publishes a custom feed`() {
        val model = PublishingViewModel(SavedStateHandle(mapOf("feedId" to "f1")), repository, session)
        model.loadPublish()
        until("the publish list") { model.publish.value != null }
        rule.setContent { PublishingSection(model) }
        rule.onNodeWithText("@me@social.example").assertIsDisplayed()
        rule.onNodeWithText("Mastodon · 3 copies").assertIsDisplayed()
        rule.onNodeWithContentDescription("Publish to @me@social.example").assertIsOn()
        rule.onNodeWithContentDescription("Publish to me.here.example").assertIsOff()
        val bluesky = model.accounts(model.publish.value).indexOfFirst { it.id == "b2" }
        rule.onAllNodesWithContentDescription("More options")[bluesky].performClick()
        rule.onNodeWithText("Publish as a Bluesky feed").assertIsDisplayed()
        rule.onNodeWithText("Backups").assertIsDisplayed()
    }

    @Test
    fun `a person source is followed with the hosted account chosen where there are several`() {
        val model = FeedSettingsViewModel(SavedStateHandle(mapOf("feedId" to "f1")), repository, session)
        rule.setContent { SourcesTab(viewModel = model) }
        until("the accounts") { model.networkAccounts.value.isNotEmpty() }
        rule.onNodeWithContentDescription("Add source").performClick()
        rule.onNodeWithText("RSS").performClick()
        rule.onNodeWithText("Person or community").performScrollTo().performClick()
        rule.onNodeWithText("Follow with").performClick()
        rule.onNodeWithText("@me@here.example").performScrollTo().performClick()
        rule.onNodeWithText("URL").performTextInput("@alice@there.example")
        rule.onNodeWithText("Add").performClick()
        until("the source") { sent("/feeds/f1/-/sources/add") != null }
        val added = Gson().fromJson(sent("/feeds/f1/-/sources/add"), Map::class.java)
        assertEquals("person", added["type"])
        assertEquals("h1", added["account"])
    }

    private fun retrofitApi(): FeedsApi = Retrofit.Builder()
        .baseUrl(server.url("/feeds/"))
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(FeedsApi::class.java)
}

/** Collects the publishing events into [into] until the job is cancelled. */
private fun collect(model: PublishingViewModel, into: MutableList<PublishEvent>) =
    CoroutineScope(Dispatchers.Unconfined).launch { model.events.collect { into.add(it) } }
