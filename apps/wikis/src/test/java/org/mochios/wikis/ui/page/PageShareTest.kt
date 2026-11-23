// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.page

import android.content.Intent
import android.os.Looper
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.IntentCompat
import androidx.lifecycle.SavedStateHandle
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.android.share.EXTRA_ADDRESS
import org.mochios.android.share.EXTRA_WORDS
import org.mochios.android.share.ShareSheet
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.mochios.wikis.api.WikisApi
import org.mochios.wikis.model.PageFetchResponse
import org.mochios.wikis.model.PageFetchResponseDeserializer
import org.mochios.wikis.model.WikiPermissions
import org.mochios.wikis.repository.WikisRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import org.mochios.android.R as MochiR

/**
 * Share from a wiki page's menu, through the system share sheet: the page,
 * for anyone reading it, by its title, the start of its text and its address;
 * the wiki itself, for those who manage it, to other apps by the mochi://
 * link another server joins it by and to Mochi by its name and address.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h1600dp")
class PageShareTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var session: SessionManager
    private lateinit var repository: WikisRepository
    private lateinit var base: String

    /** Each request: its method and its path with the query. */
    private val requests = ConcurrentLinkedQueue<Pair<String, String>>()

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                requests.add(request.method.orEmpty() to request.path.orEmpty())
                return when (path) {
                    // A reader: neither the owner nor a manager.
                    "/wikis/w1/-/info" -> ok("""{"wiki": {"id": "w1", "fingerprint": "wfp", "name": "Recipes"}, "permissions": {"view": true}}""")
                    "/wikis/w1/-/pages/home" -> ok(
                        """{"page": {"id": "p1", "slug": "home", "title": "Home", "content": "# Soups\n\nA **leek** and [potato](https://soup.example) one."},
                        "comments": {"count": 0}, "links": {"missing": []}}""",
                    )
                    "/wikis/w1/-/pages/home/comments" -> ok("""{"comments": []}""")
                    "/wikis/w1/-/share" -> ok("""{"link": "mochi://12D3KooWPeer/w1", "peer": "12D3KooWPeer", "wiki": "w1"}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        base = server.url("/").toString().trimEnd('/')
        val gson = GsonBuilder()
            .registerTypeAdapter(PageFetchResponse::class.java, PageFetchResponseDeserializer())
            .create()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/wikis/"))
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(WikisApi::class.java)
        session = SessionManager(context)
        runBlocking { session.setServerUrl(base) }
        socket = MochiWebSocket(OkHttpClient(), Gson(), object : SocketSession {
            override suspend fun serverUrl(): String = base

            override suspend fun token(app: String): String? = "token"

            override suspend fun invalidate(app: String) {
            }
        })
        repository = WikisRepository(api, FileStore(context), context)
    }

    @After
    fun end() {
        socket.disconnectAll()
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun string(id: Int, vararg values: Any) = context.getString(id, *values)

    /**
     * Waits for [done], a flag rather than a node: reading the nodes is what
     * lets the main thread run the answer the flag is waiting on.
     */
    private fun answered(done: () -> Boolean) = rule.waitUntil(5_000) {
        rule.onAllNodes(isRoot()).fetchSemanticsNodes()
        done()
    }

    /** The page, loaded, with the sheet its shares open ready. */
    private fun page(): PageViewModel {
        val model = PageViewModel(SavedStateHandle(mapOf("wikiId" to "w1", "page" to "home")), repository, session, socket)
        rule.setContent { ShareSheet(model.shares) }
        answered { model.uiState.value.page != null && model.uiState.value.wiki != null }
        return model
    }

    /**
     * What the system share sheet opened for, once it opens; the launch of
     * the test's own host activity is passed over.
     */
    private fun sheet(): Intent {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
            val started = shadowOf(context).nextStartedActivity
            if (started == null) {
                Thread.sleep(10)
            } else if (started.action == Intent.ACTION_CHOOSER) {
                return IntentCompat.getParcelableExtra(started, Intent.EXTRA_INTENT, Intent::class.java)!!
            }
        }
        throw AssertionError("the share sheet did not open")
    }

    @Test
    fun `a reader shares the page by its title and its address, which opens the rest`() {
        val model = page()
        assertEquals(WikiPermissions(view = true), model.uiState.value.permissions)
        rule.runOnIdle { model.sharePage() }
        val send = sheet()
        assertEquals("Home\n\n$base/wikis/wfp/home", send.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("Home", send.getStringExtra(EXTRA_WORDS))
        assertEquals("$base/wikis/wfp/home", send.getStringExtra(EXTRA_ADDRESS))
    }

    @Test
    fun `the wiki is shared to other apps by its join link, and to Mochi by name and address`() {
        val model = page()
        rule.runOnIdle { model.shareWiki() }
        val send = sheet()
        assertTrue(requests.any { (method, path) -> method == "POST" && path == "/wikis/w1/-/share" })
        assertEquals("mochi://12D3KooWPeer/w1", send.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("Recipes", send.getStringExtra(EXTRA_WORDS))
        assertEquals("$base/wikis/wfp", send.getStringExtra(EXTRA_ADDRESS))
    }

    private var pageShared = false
    private var wikiShared = false

    private fun menu(page: Boolean, permissions: WikiPermissions, replica: Boolean = false) {
        rule.setContent {
            PageOverflowMenu(
                expanded = true,
                onDismiss = {},
                permissions = permissions,
                commentCount = 0,
                canUnsubscribe = replica,
                shortcut = null,
                onEdit = {},
                onRename = {},
                onHistory = {},
                onComments = {},
                onDelete = {},
                onTags = {},
                onChanges = {},
                onNewPage = {},
                onSettings = {},
                onShare = { wikiShared = true },
                onUnsubscribe = {},
                onRssCopy = {},
                onRssRevoke = {},
                onSharePage = if (page) ({ pageShared = true }) else null,
            )
        }
        rule.waitForIdle()
    }

    @Test
    fun `a reader who neither owns nor manages the wiki has the page's Share`() {
        menu(page = true, permissions = WikiPermissions(view = true))
        val shares = rule.onAllNodesWithText(string(MochiR.string.share_title))
        assertEquals(1, shares.fetchSemanticsNodes().size)
        shares[0].performClick()
        assertTrue(pageShared)
    }

    @Test
    fun `a manager has the page's Share first, then the wiki's`() {
        menu(page = true, permissions = WikiPermissions(view = true, manage = true))
        val shares = rule.onAllNodesWithText(string(MochiR.string.share_title))
        assertEquals(2, shares.fetchSemanticsNodes().size)
        shares[0].performClick()
        assertTrue(pageShared)
        shares[1].performClick()
        assertTrue(wikiShared)
    }

    @Test
    fun `with no page shown, a reader has no Share`() {
        menu(page = false, permissions = WikiPermissions(view = true))
        assertEquals(0, rule.onAllNodesWithText(string(MochiR.string.share_title)).fetchSemanticsNodes().size)
    }

    @Test
    fun `with no page shown, a manager has the wiki's Share`() {
        menu(page = false, permissions = WikiPermissions(view = true, manage = true))
        rule.onAllNodesWithText(string(MochiR.string.share_title))[0].performClick()
        assertTrue(wikiShared)
    }

    @Test
    fun `a replica's manager shares the replica, as on the web`() {
        menu(page = false, permissions = WikiPermissions(view = true, manage = true), replica = true)
        assertEquals(1, rule.onAllNodesWithText(string(MochiR.string.share_title)).fetchSemanticsNodes().size)
    }
}
