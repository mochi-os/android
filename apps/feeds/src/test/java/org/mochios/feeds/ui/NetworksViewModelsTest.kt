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
import org.mochios.android.i18n.AppContext
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.mochios.feeds.api.FeedsApi
import org.mochios.feeds.api.MenuApi
import org.mochios.feeds.repository.FeedsRepository
import org.mochios.feeds.repository.SavedRepository
import org.mochios.feeds.ui.feed.FeedViewModel
import org.mochios.feeds.ui.find.FindFeedsViewModel
import org.mochios.feeds.ui.post.CreatePostScreen
import org.mochios.feeds.ui.post.CreatePostViewModel
import org.mochios.feeds.ui.post.PostDetailViewModel
import org.mochios.feeds.ui.settings.FeedSettingsViewModel
import org.mochios.feeds.ui.settings.PublishEvent
import org.mochios.feeds.ui.settings.PublishingSection
import org.mochios.feeds.ui.settings.PublishingViewModel
import org.mochios.feeds.ui.settings.SourcesTab
import org.mochios.feeds.ui.settings.FiltersSection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import org.mochios.android.api.userMessage
import androidx.compose.ui.test.onAllNodesWithText
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
import org.robolectric.shadows.ShadowToast
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

    /** The feed's sources answer, and whether Allow is next refused for want of the grant. */
    @Volatile private var sourcesAnswer = """{"sources": []}"""
    @Volatile private var allowRefused = false

    /** Holds Allow's answer back until a test lets it go, so it can see the work under way. */
    @Volatile private var allowGate: java.util.concurrent.CountDownLatch? = null

    /** What an account import answers. */
    @Volatile private var importAnswer = """{"imported": 4, "more": true}"""

    /** Whether the feed is private, so Publish to is refused with the reason. */
    @Volatile private var feedPrivate = false

    /** Holds the first publish list save back until a test lets it go. */
    @Volatile private var publishGate: java.util.concurrent.CountDownLatch? = null

    /** Whether granting a permission is refused. */
    @Volatile private var grantRefused = false

    /** Whether a report and a filter's add are refused. */
    @Volatile private var reportRefused = false
    @Volatile private var filterRefused = false

    /** What a new post answers. */
    @Volatile private var createAnswer = """{"id": "p9"}"""

    /** Answers the next source adds take, in order, before the usual one. */
    private val addAnswers = ConcurrentLinkedQueue<MockResponse>()

    private fun permissionRequired() = MockResponse().setResponseCode(403)
        .setBody("""{"app": "feeds", "error": "permission_required", "permission": "url:blog.example", "restricted": false}""")

    private fun feedRefused() = MockResponse().setResponseCode(502).setBody("""{"error": "Feed returned status 404"}""")

    @Before
    fun begin() {
        // Errors are shown through userMessage(), which reads the app's catalogue.
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                val body = request.body.readUtf8()
                requests.add(path to body)
                return when (path) {
                    "/feeds/-/info" -> ok("""{"feeds": [{"id": "f1", "fingerprint": "f1", "name": "Mine", "owner": 1, "privacy": "public"}, {"id": "f2", "fingerprint": "f2", "name": "Theirs", "owner": 0}]}""")
                    "/feeds/f1/-/info" -> ok("""{"feed": {"id": "f1", "fingerprint": "f1", "name": "Mine", "privacy": "public"}, "permissions": {"view": true, "react": true, "comment": true, "manage": true}}""")
                    "/feeds/f1/-/posts", "/feeds/-/posts" -> ok("""{"posts": $listing}""")
                    "/feeds/f1/-/p1" -> ok("""{"posts": [$post], "permissions": {"view": true, "react": true, "comment": true, "manage": true}}""")
                    "/feeds/f1/-/p1/tags" -> ok("""{"tags": []}""")
                    "/feeds/f1/-/post/new" -> ok("""{"feeds": [{"id": "f1", "fingerprint": "f1", "name": "Mine", "privacy": "public"}]}""")
                    "/feeds/-/accounts/list" -> raw("""[{"id": "b3", "type": "bluesky", "identifier": "me3.here.example", "hosted": true},
                        {"id": "h2", "type": "fediverse", "identifier": "@me2@here.example", "hosted": true},
                        {"id": "h1", "type": "fediverse", "identifier": "@me@here.example", "hosted": true},
                        {"id": "l1", "type": "mastodon", "identifier": "@me@social.example", "hosted": false},
                        {"id": "b1", "type": "bluesky", "identifier": "me.bsky.social", "hosted": false}]""")
                    "/feeds/f1/-/p1/vote" -> ok("""{"post": "p1", "poll": {"options": [{"name": "Tea", "votes": 1}, {"name": "Coffee", "votes": 1}], "multiple": false, "expires": 0, "voters": 2}, "voted": [1]}""")
                    "/feeds/f1/-/p1/pin" -> ok("""{"post": "p1", "pinned": 1700000000}""")
                    "/feeds/f1/-/p1/unpin" -> ok("""{"post": "p1", "pinned": 0}""")
                    "/feeds/f1/-/p1/boost" -> ok("""{"copies": [{"address": "@me2@here.example", "network": "fediverse", "remote": "y", "url": ""}]}""")
                    "/feeds/f1/-/p1/c1/report" -> if (reportRefused) {
                        MockResponse().setResponseCode(502).setBody("""{"error": "The report could not be delivered"}""")
                    } else ok("""{"done": "x"}""")
                    "/feeds/f1/-/p1/c1/block", "/feeds/f1/-/p1/c1/mute" -> ok("""{"done": "x"}""")
                    "/feeds/f1/-/p1/comment/create" -> ok("""{"comment": {"id": "c2"}}""")
                    "/feeds/-/read-all" -> ok("""{"ok": true, "read": 1, "feeds": 2}""")
                    "/feeds/f1/-/post/create" -> ok(createAnswer)
                    "/feeds/f1/-/privacy" -> ok("""{"privacy": "private"}""")
                    "/feeds/f1/-/publish" -> if (feedPrivate) {
                        MockResponse().setResponseCode(403).setBody("""{"error": "Only a public feed publishes to other networks"}""")
                    } else raw("""{"accounts": [{"id": "l1", "type": "mastodon", "identifier": "@me@social.example"},
                        {"id": "b2", "type": "bluesky", "identifier": "me.here.example", "hosted": true}],
                        "publish": ["l1"], "copies": {"l1": 3}, "generators": {}}""")
                    "/feeds/f1/-/publish/set" -> {
                        publishGate?.await(5, TimeUnit.SECONDS)
                        // The list saved is the one sent.
                        val saved = java.net.URLDecoder.decode(body.substringAfter("accounts="), "UTF-8")
                        raw("""{"publish": $saved, "kept": {"l1": 3}}""")
                    }
                    "/feeds/f1/-/publish/forget" -> raw("""{"deleted": 3}""")
                    "/feeds/f1/-/generator/set" -> raw("""{"generators": {"b2": "at://did/app.bsky.feed.generator/f1"}}""")
                    "/feeds/f1/-/import" -> raw(importAnswer)
                    "/feeds/f1/-/backups" -> raw("""{"files": ["posts-2026-11.jsonl"], "repository": "The repository could not be saved: bsky.example answered 400: RepoNotFound"}""")
                    "/feeds/f1/-/filters" -> ok("""{"filters": [{"id": "x", "phrase": "zebra"}, {"id": "y", "phrase": "Apple"}]}""")
                    "/feeds/f1/-/filters/add" -> if (filterRefused) {
                        MockResponse().setResponseCode(400).setBody("""{"error": "Invalid phrase"}""")
                    } else ok("""{"filters": [{"id": "x", "phrase": "zebra"}, {"id": "y", "phrase": "Apple"}, {"id": "z", "phrase": "mango"}]}""")
                    "/feeds/f1/-/sources" -> ok(sourcesAnswer)
                    "/feeds/f1/-/sources/allow" -> if (allowRefused) {
                        allowRefused = false
                        MockResponse().setResponseCode(403).setBody("""{"app": "feeds", "error": "permission_required", "permission": "url:wasabi.example", "restricted": false}""")
                    } else {
                        allowGate?.await(5, TimeUnit.SECONDS)
                        ok("""{"imported": 2, "more": false, "waiting": []}""")
                    }
                    "/feeds/-/permissions/grant" -> if (grantRefused) {
                        MockResponse().setResponseCode(403).setBody("""{"error": "Not allowed to grant that"}""")
                    } else ok("""{"status": "granted"}""")
                    "/feeds/f1/-/sources/add" -> addAnswers.poll() ?: ok("""{"source": {"id": "s1"}, "ingested": 2}""")
                    "/feeds/f1/-/sources/import" -> ok("""{"imported": 5, "more": false, "total": 5, "waiting": [{"server": "wasabi.example", "count": 1}]}""")
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
        repository = FeedsRepository(retrofit.create(FeedsApi::class.java), retrofit.create(MenuApi::class.java), FileStore(context), context, SessionManager(context))
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
    fun `a boost of a post from the fediverse is made from the first hosted account there, by address, and the copies follow`() {
        val model = detail()
        until("the hosted accounts") { model.hosted.value.isNotEmpty() }
        // The hosted Bluesky account comes first, but the post is from the fediverse.
        assertEquals(listOf("b3", "h2", "h1"), model.hosted.value.map { it.id })
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
    fun `every feed is marked read at once from All feeds`() {
        val model = FeedViewModel(
            SavedStateHandle(mapOf("feedId" to "__all__")),
            repository, SavedRepository(retrofitApi(), Gson(), session), socket, session, context,
        )
        until("the posts") { model.posts.value.isNotEmpty() }
        model.markAllRead()
        until("the read") { sent("/feeds/-/read-all") != null }
        assertFalse(requests.any { it.first == "/feeds/f1/-/read-all" })
    }

    @Test
    fun `a feed card pins, boosts and votes on its post`() {
        val model = FeedViewModel(SavedStateHandle(mapOf("feedId" to "f1")), repository, SavedRepository(retrofitApi(), Gson(), session), socket, session, context)
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
    fun `a new post's accounts are chosen last, below what it says and carries`() {
        val model = CreatePostViewModel(SavedStateHandle(mapOf("feedId" to "f1")), repository, context)
        until("the publish list") { model.publish.value != null }
        rule.setContent { CreatePostScreen(onNavigateBack = {}, viewModel = model) }
        rule.waitForIdle()
        // Where each sits in the form, scrolled out of view or not.
        val top = { text: String -> rule.onNodeWithText(text).fetchSemanticsNode().positionInRoot.y }
        val publish = top("Also post to")
        for (above in listOf("Post content", "Poll")) {
            assertTrue("Also post to, at $publish, sits below $above, at ${top(above)}", publish > top(above))
        }
    }

    @Test
    fun `what a post's copies went without is said once it is made`() {
        createAnswer = """{"id": "p9", "unsent": ["@me@social.example received the post without a.png"]}"""
        val model = CreatePostViewModel(SavedStateHandle(mapOf("feedId" to "f1")), repository, context)
        until("the publish list") { model.publish.value != null }
        rule.setContent { CreatePostScreen(onNavigateBack = {}, viewModel = model) }
        model.setBody("Hello")
        model.createPost()
        until("the post") { model.postSuccess.value }
        assertEquals(listOf("@me@social.example received the post without a.png"), model.unsent.value)
        rule.waitForIdle()
        assertEquals("@me@social.example received the post without a.png", ShadowToast.getTextOfLatestToast())
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
    fun `an account's Backups list its files and say why the last backup kept no repository`() {
        val model = PublishingViewModel(SavedStateHandle(mapOf("feedId" to "f1")), repository, session)
        model.openBackups(org.mochios.feeds.model.PublishAccount(id = "b1"))
        until("the backups") { model.backups.value?.files != null }
        assertEquals(listOf("posts-2026-11.jsonl"), model.backups.value!!.files)
        assertEquals("The repository could not be saved: bsky.example answered 400: RepoNotFound", model.backups.value!!.repository)
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
    fun `a file import asks at once for the server people wait on, and the Sources list shows the servers`() {
        sourcesAnswer = """{"sources": [], "waiting": [{"server": "wasabi.example", "count": 2}]}"""
        allowRefused = true
        val model = FeedSettingsViewModel(SavedStateHandle(mapOf("feedId" to "f1")), repository, session)
        model.importFollows("Account address\nbob@wasabi.example\n")
        until("the import") { model.imported.value != null }
        until("the server asked for") { model.waitingPermission.value != null }
        assertEquals("url:wasabi.example", model.waitingPermission.value!!.permission)
        until("the waiting servers") { model.waiting.value.isNotEmpty() }
        assertEquals(2, model.waiting.value.single().count)
        model.denyWaitingPermission()
        assertNull(model.waitingPermission.value)
        assertNull(model.allowed.value)
    }

    @Test
    fun `allowing a server asks for the grant it is refused, then adds the people waiting on it`() {
        allowRefused = true
        val model = FeedSettingsViewModel(SavedStateHandle(mapOf("feedId" to "f1")), repository, session)
        model.allowWaiting("wasabi.example")
        until("the permission asked") { model.waitingPermission.value != null }
        assertEquals("url:wasabi.example", model.waitingPermission.value!!.permission)
        assertEquals("feeds", model.waitingPermission.value!!.app)
        assertFalse(model.addingWaiting.value)
        val gate = java.util.concurrent.CountDownLatch(1)
        allowGate = gate
        model.allowWaitingPermission()
        // The import button stays busy while the people are added.
        until("the adding under way") { model.addingWaiting.value }
        gate.countDown()
        until("the people added") { model.allowed.value != null }
        until("the adding done") { !model.addingWaiting.value }
        assertEquals(2, model.allowed.value!!.imported)
        assertTrue(sent("/feeds/-/permissions/grant")!!.contains("permission=url%3Awasabi.example"))
        assertEquals(2, requests.count { it.first == "/feeds/f1/-/sources/allow" })
    }

    @Test
    fun `an account import asks at once for the server people wait on, and allowing it adds them`() {
        importAnswer = """{"imported": 4, "more": false, "waiting": [{"server": "wasabi.example", "count": 2}]}"""
        allowRefused = true
        val model = PublishingViewModel(SavedStateHandle(mapOf("feedId" to "f1")), repository, session)
        val events = mutableListOf<PublishEvent>()
        val job = collect(model, events)
        model.loadPublish()
        until("the publish list") { model.publish.value != null }
        rule.setContent { PublishingSection(model) }
        val menus = { rule.onAllNodesWithContentDescription("More options").fetchSemanticsNodes().size }
        assertEquals(2, menus())
        model.importFrom(org.mochios.feeds.model.PublishAccount(id = "l1"), "follows")
        until("the server asked for") { model.waitingPermission.value != null }
        assertEquals("url:wasabi.example", model.waitingPermission.value!!.permission)
        val gate = java.util.concurrent.CountDownLatch(1)
        allowGate = gate
        model.allowWaitingPermission()
        // The account shows busy while its people are added, not only during the import.
        until("the adding under way") { model.addingWaiting.value }
        assertEquals("l1", model.asking.value)
        // The account's menu is a spinner meanwhile.
        rule.waitForIdle()
        assertEquals(1, menus())
        gate.countDown()
        until("the people added") { events.count { it is PublishEvent.Imported } == 2 }
        until("the adding done") { !model.addingWaiting.value }
        rule.waitForIdle()
        assertEquals(2, menus())
        job.cancel()
        assertEquals(PublishEvent.Imported("follows", 4, false), events.filterIsInstance<PublishEvent.Imported>()[0])
        assertEquals(PublishEvent.Imported("follows", 2, false), events.filterIsInstance<PublishEvent.Imported>()[1])
        assertTrue(sent("/feeds/-/permissions/grant")!!.contains("permission=url%3Awasabi.example"))
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
    @Test
    fun `a feed is made private from its settings, and its privacy follows`() {
        val model = FeedSettingsViewModel(SavedStateHandle(mapOf("feedId" to "f1")), repository, session)
        model.loadFeedInfo()
        until("the feed") { model.feedInfo.value != null }
        assertEquals("public", model.feedInfo.value!!.privacy)
        model.setPrivacy(false)
        until("the privacy") { model.feedInfo.value!!.privacy == "private" }
        assertEquals("privacy=private", sent("/feeds/f1/-/privacy"))
        until("the save to end") { !model.savingPrivacy.value }
    }

    @Test
    fun `Publish to says why a private feed publishes nowhere, and lists the accounts once it is public`() {
        feedPrivate = true
        val model = PublishingViewModel(SavedStateHandle(mapOf("feedId" to "f1")), repository, session)
        model.loadPublish()
        until("the refusal") { model.refusal.value != null }
        assertEquals("Only a public feed publishes to other networks", model.refusal.value!!.message)
        assertNull(model.publish.value)
        assertTrue(org.mochios.feeds.ui.settings.publishable(model.publish.value, model.refusal.value))
        feedPrivate = false
        model.loadPublish()
        until("the accounts") { model.publish.value != null }
        assertNull(model.refusal.value)
    }
    /** Opens the Add dialog, types an RSS address and adds it. */
    private fun addThroughDialog(model: FeedSettingsViewModel) {
        rule.setContent { SourcesTab(viewModel = model) }
        rule.onNodeWithContentDescription("Add source").performClick()
        rule.onNodeWithText("URL").performTextInput("https://blog.example/feed")
        rule.onNodeWithText("Add").performClick()
        until("the permission prompt") { model.pendingPermission.value != null }
    }

    private fun adds() = requests.count { it.first == "/feeds/f1/-/sources/add" }

    @Test
    fun `a source add that needs a permission keeps the Add dialog under the prompt, and Deny returns to it as typed`() {
        addAnswers.add(permissionRequired())
        val model = FeedSettingsViewModel(SavedStateHandle(mapOf("feedId" to "f1")), repository, session)
        addThroughDialog(model)
        rule.onNodeWithText("Deny").performClick()
        until("the prompt to go") { model.pendingPermission.value == null }
        rule.onNodeWithText("https://blog.example/feed").assertIsDisplayed()
        assertEquals(1, adds())
    }

    @Test
    fun `Allow re-runs the add, and its refusal shows in the Add dialog with what was typed`() {
        addAnswers.add(permissionRequired())
        addAnswers.add(feedRefused())
        val model = FeedSettingsViewModel(SavedStateHandle(mapOf("feedId" to "f1")), repository, session)
        addThroughDialog(model)
        rule.onNodeWithText("Allow").performClick()
        until("the refusal") { model.addSourceError.value != null }
        assertEquals(2, adds())
        rule.onNodeWithText("https://blog.example/feed").assertIsDisplayed()
        rule.onNodeWithText(model.addSourceError.value!!.userMessage()).assertIsDisplayed()
    }

    @Test
    fun `Allow re-runs the add, and the Add dialog closes once it is added`() {
        addAnswers.add(permissionRequired())
        val model = FeedSettingsViewModel(SavedStateHandle(mapOf("feedId" to "f1")), repository, session)
        addThroughDialog(model)
        rule.onNodeWithText("Allow").performClick()
        until("the add") { adds() == 2 }
        until("the dialog to close") { rule.onAllNodesWithText("https://blog.example/feed").fetchSemanticsNodes().isEmpty() }
        assertNull(model.addSourceError.value)
    }

    @Test
    fun `a refused grant shows in the Add dialog, which keeps what was typed`() {
        addAnswers.add(permissionRequired())
        grantRefused = true
        val model = FeedSettingsViewModel(SavedStateHandle(mapOf("feedId" to "f1")), repository, session)
        addThroughDialog(model)
        rule.onNodeWithText("Allow").performClick()
        until("the refusal") { model.addSourceError.value != null }
        assertEquals("Not allowed to grant that", model.addSourceError.value!!.message)
        assertEquals(1, adds())
        rule.onNodeWithText("https://blog.example/feed").assertIsDisplayed()
        rule.onNodeWithText("Not allowed to grant that").assertIsDisplayed()
    }

    @Test
    fun `a fresh Add dialog starts without an earlier add's refusal`() {
        addAnswers.add(feedRefused())
        val model = FeedSettingsViewModel(SavedStateHandle(mapOf("feedId" to "f1")), repository, session)
        model.addSource("https://blog.example/feed", "rss")
        until("the refusal") { model.addSourceError.value != null }
        val message = model.addSourceError.value!!.userMessage()
        rule.setContent { SourcesTab(viewModel = model) }
        rule.onNodeWithContentDescription("Add source").performClick()
        rule.onNodeWithText("URL").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText(message).fetchSemanticsNodes().size)
    }
    @Test
    fun `a publish switch flipped while another account's save runs waits for it, and both end up in the list`() {
        val gate = java.util.concurrent.CountDownLatch(1)
        publishGate = gate
        val model = PublishingViewModel(SavedStateHandle(mapOf("feedId" to "f1")), repository, session)
        model.loadPublish()
        until("the publish list") { model.publish.value != null }
        assertEquals(listOf("l1"), model.publish.value!!.publish)
        val accounts = model.publish.value!!.accounts
        model.toggle(accounts.first { it.id == "b2" }, true)
        until("the first save") { sent("/feeds/f1/-/publish/set") != null }
        model.toggle(accounts.first { it.id == "l1" }, false)
        assertEquals(setOf("b2", "l1"), model.busy.value)
        publishGate = null
        gate.countDown()
        until("both saves") { requests.count { it.first == "/feeds/f1/-/publish/set" } == 2 && model.busy.value.isEmpty() }
        assertEquals(listOf("b2"), model.publish.value!!.publish)
    }
    @Test
    fun `a report says whether the server took it, so a refused one keeps its dialog`() {
        val model = detail()
        reportRefused = true
        var done: Boolean? = null
        model.moderate("c1", "report", "Spam") { done = it }
        until("the refusal") { done != null }
        assertEquals(false, done)
        reportRefused = false
        done = null
        model.moderate("c1", "report", "Spam") { done = it }
        until("the report") { done != null }
        assertEquals(true, done)
    }

    @Test
    fun `a refused filter phrase stays in the field, and one the server takes is cleared`() {
        val model = PublishingViewModel(SavedStateHandle(mapOf("feedId" to "f1")), repository, session)
        model.loadFilters()
        until("the filters") { model.filters.value.isNotEmpty() }
        rule.setContent { FiltersSection(model) }
        filterRefused = true
        rule.onNodeWithText("Phrase to hide").performTextInput("mango")
        rule.onNodeWithText("Add").performClick()
        until("the refusal") { sent("/feeds/f1/-/filters/add") != null && model.busy.value.isEmpty() }
        rule.onNodeWithText("mango").assertIsDisplayed()
        filterRefused = false
        rule.onNodeWithText("Add").performClick()
        until("the filter") { model.filters.value.size == 3 }
        assertEquals(0, rule.onAllNodesWithText("mango").fetchSemanticsNodes().count { node ->
            node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.EditableText)
        })
    }
}

/** Collects the publishing events into [into] until the job is cancelled. */
private fun collect(model: PublishingViewModel, into: MutableList<PublishEvent>) =
    CoroutineScope(Dispatchers.Unconfined).launch { model.events.collect { into.add(it) } }
