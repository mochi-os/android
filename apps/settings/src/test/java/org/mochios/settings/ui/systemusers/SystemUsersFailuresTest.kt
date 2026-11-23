// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.systemusers

import android.os.Looper
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.AuthApi
import org.mochios.android.auth.AuthRepository
import org.mochios.android.auth.PasskeyManager
import org.mochios.android.auth.SessionManager
import org.mochios.android.auth.TokenApi
import org.mochios.android.i18n.AppContext
import org.mochios.settings.R
import org.mochios.settings.api.AccountApi
import org.mochios.settings.api.SystemUsersApi
import org.mochios.settings.ui.login.SettingsStepUpClient
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** The System users screen over a server: what a failure says, and paging a search. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h1600dp")
class SystemUsersFailuresTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private val lists = AtomicInteger()

    /** The create's answer. */
    @Volatile private var created: MockResponse = json("{}")

    /** Holds each read of the list after the first back until released, when set. */
    @Volatile private var gate: CountDownLatch? = null

    private val messages = CopyOnWriteArrayList<String>()
    private var collector: Job? = null

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setBody(body).setHeader("Content-Type", "application/json")

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    /** A page of [count] matches: the users from [offset], at most [limit] of them. */
    private fun page(form: Map<String, String>): MockResponse {
        val count = if (form["search"].isNullOrEmpty()) 30 else 40
        val offset = form["offset"]!!.toInt()
        val users = (offset until minOf(offset + form["limit"]!!.toInt(), count))
            .joinToString(",") { """{"uid": "u$it", "username": "user$it@a.example"}""" }
        return json("""{"users": [$users], "count": $count}""")
    }

    private fun form(raw: String): Map<String, String> = raw.split("&").filter { it.contains("=") }
        .associate { it.split("=", limit = 2).let { (k, v) -> URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8") } }

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path.orEmpty().substringBefore("?").removePrefix("/")) {
                    "settings/-/system/users/list" -> {
                        val body = form(request.body.readUtf8())
                        if (lists.incrementAndGet() > 1) gate?.await(5, TimeUnit.SECONDS)
                        page(body)
                    }
                    "settings/-/system/users/create" -> created
                    else -> MockResponse().setResponseCode(404)
                }
        }
        server.start()
    }

    @After
    fun end() {
        gate?.countDown()
        collector?.cancel()
        server.shutdown()
    }

    private fun model(): SystemUsersViewModel {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val session = SessionManager(context)
        runBlocking { session.clearAll() }
        val made = SystemUsersViewModel(
            retrofit.create(SystemUsersApi::class.java),
            SettingsStepUpClient(retrofit.create(AccountApi::class.java), PasskeyManager(context)),
            AuthRepository(retrofit.create(AuthApi::class.java), retrofit.create(TokenApi::class.java), session),
        )
        collector = CoroutineScope(Dispatchers.Unconfined).launch { made.messages.collect { messages += it } }
        until { !made.uiState.value.isLoading && made.uiState.value.users.isNotEmpty() }
        return made
    }

    /** A refusal used to show the server's reason, then "Failed to create user" queued behind it. */
    @Test
    fun `a refused action says the server's reason once`() {
        created = json("""{"error": "That address is taken"}""", 400)
        val model = model()
        model.create("user0@a.example", "user") {}
        until { messages.isNotEmpty() }
        val settled = System.currentTimeMillis() + 200
        until { System.currentTimeMillis() > settled }
        assertEquals(listOf("That address is taken"), messages)
        assertNull(model.uiState.value.error)
        assertNull(model.uiState.value.toast)
    }

    @Test
    fun `a failure the server gives no reason for is said in the action's own words`() {
        created = json("{}", 500)
        val model = model()
        model.create("user0@a.example", "user") {}
        until { messages.isNotEmpty() }
        assertEquals(listOf(context.getString(R.string.system_users_toast_create_failed)), messages)
    }

    private fun shown(text: String) = rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    /** The pager used to be hidden while a search was active, so matches past the first page were out of reach. */
    @Test
    fun `a search matching more users than a page is paged`() {
        val model = model()
        rule.setContent { SystemUsersScreen(onBack = {}, viewModel = model) }
        model.setSearch("user")
        until { lists.get() >= 2 && !model.uiState.value.isLoading }
        rule.waitForIdle()
        assertEquals(40, model.uiState.value.count)
        val next = hasText(context.getString(R.string.system_users_next))
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(next)
        rule.onNode(next).assertExists()
    }

    /** A page change used to blank the list to a spinner while it was read. */
    @Test
    fun `the users stay on screen while the next page is read`() {
        val model = model()
        rule.setContent { SystemUsersScreen(onBack = {}, viewModel = model) }
        rule.waitForIdle()
        gate = CountDownLatch(1)
        model.nextPage()
        until { lists.get() >= 2 }
        rule.waitForIdle()
        assertEquals(true, shown("user0@a.example"))
        gate!!.countDown()
    }
}
