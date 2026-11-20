// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.networks

import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.forums.R
import org.mochios.forums.api.ForumsApi
import org.mochios.forums.repository.ForumsRepository
import org.mochios.forums.ui.find.CommunitiesSections
import org.mochios.forums.ui.find.CommunitiesViewModel
import org.mochios.forums.ui.find.FindForumsScreen
import org.mochios.forums.ui.find.FindForumsViewModel
import org.mochios.forums.ui.find.communityExample
import org.mochios.forums.ui.find.settable
import org.mochios.forums.ui.settings.CommunitySection
import org.mochios.forums.ui.settings.CommunityViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/**
 * Where the server offers the fediverse and the user has no account there:
 * the forum's Community section shows only where it may be published or
 * withdrawn, and Find offers to set up an account to join a community through.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ForumsNetworkSetupTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var repository: ForumsRepository

    private val requests = ConcurrentLinkedQueue<String>()

    /** The user's accounts on other networks, as the server lists them. */
    @Volatile private var accounts = "[]"

    /** The networks the server hosts accounts on. */
    @Volatile private var networks = """["fediverse"]"""

    /** The forum as a community, as the server answers it. */
    @Volatile private var community = """{"published": false, "offered": true, "domains": ["a.example"], "handles": []}"""

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                requests.add(path)
                return when (path) {
                    "/forums/-/accounts/list" -> MockResponse().setBody(accounts)
                    "/forums/-/networks" -> ok("""{"networks": $networks}""")
                    "/forums/f1/-/publish" -> ok(community)
                    "/forums/-/directory/search" -> ok("""{"results": []}""")
                    "/forums/-/recommendations" -> ok("""{"forums": []}""")
                    "/forums/-/communities/lookup" ->
                        ok("""{"id": "https://lemmy.world/c/technology", "name": "Technology", "handle": "technology@lemmy.world", "avatar": ""}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/forums/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ForumsApi::class.java)
        repository = ForumsRepository(api, FileStore(context), context, SessionManager(context))
    }

    @After
    fun end() {
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun until(what: String, done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out waiting for $what; requests ${requests.toList()}" }
            Thread.sleep(10)
        }
    }

    private fun text(id: Int): String = context.getString(id)

    private fun section(): CommunityViewModel {
        val model = CommunityViewModel(SavedStateHandle(mapOf("forumId" to "f1")), repository)
        model.load(sensitive = false)
        until("the community") { model.state.value != null }
        rule.setContent { CommunitySection(model) }
        rule.waitForIdle()
        return model
    }

    private fun find(): CommunitiesViewModel {
        val model = CommunitiesViewModel(repository)
        until("the accounts and networks") { model.listed.value && model.networks.value.isNotEmpty() }
        return model
    }

    @Test
    fun `the Community section is hidden where the server hosts no fediverse accounts`() {
        community = """{"published": false, "offered": false, "domains": ["a.example"], "handles": []}"""
        section()
        rule.onNodeWithText(text(R.string.forums_community)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.forums_community_publish)).assertDoesNotExist()
    }

    @Test
    fun `the Community section offers publishing where the server hosts fediverse accounts`() {
        section()
        rule.onNodeWithText(text(R.string.forums_community)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.forums_community_publish)).assertIsDisplayed()
    }

    @Test
    fun `a published community is shown where the server no longer hosts, so it may be withdrawn`() {
        community = """{"published": true, "offered": false, "address": "https://a.example/c/books", "identifier": "!books@a.example", "domains": []}"""
        section()
        rule.onNodeWithText(text(R.string.forums_community_withdraw)).assertIsDisplayed()
    }

    @Test
    fun `a community searched for with no account to join through offers to set one up`() {
        // A linked Lemmy account is no account to join through.
        accounts = """[{"id": "l1", "type": "lemmy", "identifier": "me@lemmy.example", "hosted": false}]"""
        val model = find()
        assertEquals(listOf("fediverse"), model.networks.value)
        val opened = mutableListOf<String>()
        rule.setContent {
            CommunitiesSections(
                viewModel = model,
                onMessage = {},
                onOpenForum = {},
                typed = "!technology@lemmy.world",
                onOpenLink = { opened += it },
            )
        }
        rule.onNodeWithText("ActivityPub").assertIsDisplayed()
        rule.onNodeWithText(text(R.string.forums_setup)).performClick()
        assertEquals(listOf("settings/user/networks?network=fediverse"), opened)
        // It stands in for "no forums found".
        assertTrue(settable("!technology@lemmy.world", model.listed.value, model.hosted.value, model.networks.value))
    }

    @Test
    fun `Find gives a community's address as its example, and offers to set an account up in place of no forums found`() {
        val communities = find()
        val model = FindForumsViewModel(SavedStateHandle(), repository)
        rule.setContent { FindForumsScreen(onBack = {}, onForumSubscribed = {}, viewModel = model, communities = communities) }
        rule.onNodeWithText(context.getString(R.string.forums_find_search_example, "!name@example.social")).assertExists()
        rule.runOnIdle { model.updateSearchQuery("!technology@lemmy.world") }
        until("the search") { requests.contains("/forums/-/directory/search") && !model.uiState.value.isSearching }
        rule.waitForIdle()
        rule.onNodeWithText(text(R.string.forums_setup)).assertExists()
        rule.onNodeWithText(text(R.string.forums_find_no_results)).assertDoesNotExist()
    }

    @Test
    fun `no way to set one up where the server hosts no fediverse accounts`() {
        networks = """["bluesky"]"""
        val model = find()
        rule.setContent {
            CommunitiesSections(viewModel = model, onMessage = {}, onOpenForum = {}, typed = "!technology@lemmy.world")
        }
        rule.onNodeWithText(text(R.string.forums_setup)).assertDoesNotExist()
        assertFalse(settable("!technology@lemmy.world", model.listed.value, model.hosted.value, model.networks.value))
    }

    @Test
    fun `a community searched for with a hosted account shows the community, not a way to set one up`() {
        accounts = """[{"id": "h1", "type": "fediverse", "identifier": "amy@here.example", "hosted": true}]"""
        val model = find()
        model.look("!technology@lemmy.world")
        until("the community") { model.found.value.community != null }
        rule.setContent {
            CommunitiesSections(viewModel = model, onMessage = {}, onOpenForum = {}, typed = "!technology@lemmy.world")
        }
        rule.onNodeWithText("Technology").assertIsDisplayed()
        rule.onNodeWithText(text(R.string.forums_setup)).assertDoesNotExist()
    }

    @Test
    fun `nothing to set up for a search that names no community, or before the accounts are read`() {
        assertFalse(settable("cooking", true, emptyList(), listOf("fediverse")))
        assertFalse(settable("!technology@lemmy.world", false, emptyList(), listOf("fediverse")))
        assertTrue(settable("!technology@lemmy.world", true, emptyList(), listOf("fediverse")))
    }

    @Test
    fun `the search's example is a community's address where the server hosts fediverse accounts`() {
        assertEquals("!name@example.social", communityExample(listOf("bluesky", "fediverse")))
        assertNull(communityExample(listOf("bluesky")))
        assertNull(communityExample(emptyList()))
    }
}
