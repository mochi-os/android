// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.networks

import android.os.Looper
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.PasskeyManager
import org.mochios.android.i18n.AppContext
import org.mochios.settings.api.AccountApi
import org.mochios.settings.api.DomainsApi
import org.mochios.settings.api.NetworksApi
import org.mochios.settings.api.SessionsApi
import org.mochios.settings.api.SystemStatusApi
import org.mochios.settings.ui.handles.SystemHandlesViewModel
import org.mochios.settings.ui.home.SettingsHomeViewModel
import org.mochios.settings.ui.login.SettingsStepUpClient
import org.mochios.settings.ui.sessions.SessionsViewModel
import org.mochios.settings.ui.systemstatus.SystemStatusViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/** The Sessions screen's sign-ins and the Status screen's other networks, against a server. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OwnerViewModelsTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private val requests = CopyOnWriteArrayList<Pair<String, String>>()

    /** Whether the server says it offers other networks. */
    @Volatile private var offered = true

    /** How many tokens the sessions answer still lists. */
    @Volatile private var signins = 1

    /** How many handles the registry says the search matches. */
    @Volatile private var matched = 60

    /** The query of each registry request, in order. */
    private val registry = CopyOnWriteArrayList<Map<String, String>>()

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?").removePrefix("/")
                requests += path to request.body.readUtf8()
                return when (path) {
                    "settings/-/user/account/sessions" -> json(
                        """{"sessions": [], "passwords": [{"hash": "p", "kind": "password", "used": 5}],
                           "signins": [${if (signins > 0) """{"hash": "s", "kind": "signin", "used": 1}""" else ""}]}""",
                    )
                    "settings/-/user/account/token/revoke" -> {
                        signins = 0
                        json("""{"ok": true}""")
                    }
                    "settings/-/system/settings/list" -> json("""{"settings": [], "server": {}}""")
                    "settings/-/system/networks" -> json(
                        """{"development": true, "networks": {"matrix": {"state": "working", "keys": [{"domain": "m.example", "key": "ed25519:a"}]}}}""",
                    )
                    "settings/-/system/networks/key/replace" -> json("""{"key": "ed25519:b"}""")
                    "settings/-/domains/data" -> json("""{"domains": [], "admin": true}""")
                    "settings/-/user/networks/data" -> json("""{"offered": $offered}""")
                    "settings/-/system/handles/list" -> {
                        val query = request.requestUrl!!.let { url -> url.queryParameterNames.associateWith { url.queryParameter(it).orEmpty() } }
                        registry += query
                        val offset = query["offset"]!!.toInt()
                        val page = (offset until minOf(offset + query["limit"]!!.toInt(), matched))
                            .joinToString(",") { """{"domain": "a.example", "name": "name$it"}""" }
                        json("""{"handles": [$page], "count": $matched, "domains": ["a.example"]}""")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun end() = server.shutdown()

    private fun json(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "application/json")

    private val retrofit by lazy {
        Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build()
    }

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    private fun form(path: String): Map<String, String> = requests.last { it.first == path }.second
        .split("&").filter { it.contains("=") }
        .associate { it.split("=", limit = 2).let { (k, v) -> URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8") } }

    @Test
    fun `the registry is searched and paged on the server, a new search from the first page`() {
        val model = SystemHandlesViewModel(retrofit.create(NetworksApi::class.java))
        until { !model.uiState.value.isLoading }
        assertEquals(mapOf("search" to "", "limit" to "25", "offset" to "0"), registry.last())
        assertEquals(60, model.uiState.value.count)
        assertEquals("name0", model.uiState.value.handles.first().name)
        // On the first page there is no previous one to ask for.
        model.previousPage()
        Thread.sleep(300)
        shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.MILLISECONDS)
        assertEquals(1, registry.size)
        model.nextPage()
        until { registry.size == 2 && !model.uiState.value.isLoading }
        assertEquals("25", registry.last()["offset"])
        assertEquals("name25", model.uiState.value.handles.first().name)
        model.nextPage()
        until { registry.size == 3 && !model.uiState.value.isLoading }
        assertEquals("50", registry.last()["offset"])
        // The last page: no further.
        model.nextPage()
        model.previousPage()
        until { registry.size == 4 && !model.uiState.value.isLoading }
        assertEquals("25", registry.last()["offset"])
        model.setSearch("ali")
        until { registry.size == 5 && !model.uiState.value.isLoading }
        assertEquals(mapOf("search" to "ali", "limit" to "25", "offset" to "0"), registry.last())
        model.nextPage()
        until { registry.size == 6 && !model.uiState.value.isLoading }
        assertEquals("25", registry.last()["offset"])
        // A new page size starts from the first page.
        model.setLimit(10)
        until { registry.size == 7 && !model.uiState.value.isLoading }
        assertEquals(mapOf("search" to "ali", "limit" to "10", "offset" to "0"), registry.last())
    }

    @Test
    fun `a page left past the end steps back to the last page with handles`() {
        val model = SystemHandlesViewModel(retrofit.create(NetworksApi::class.java))
        until { !model.uiState.value.isLoading }
        model.nextPage()
        until { registry.size == 2 && !model.uiState.value.isLoading }
        model.nextPage()
        until { registry.size == 3 && !model.uiState.value.isLoading }
        // The page's handles go, as a release of the last of them leaves it.
        matched = 50
        model.refresh(quiet = true)
        until { registry.size == 5 && model.uiState.value.handles.firstOrNull()?.name == "name25" }
        assertEquals("50", registry[3]["offset"])
        assertEquals("25", registry[4]["offset"])
        assertEquals(25, model.uiState.value.offset)
        assertEquals("name25", model.uiState.value.handles.first().name)
    }

    @Test
    fun `a sign-in is revoked by its hash and the list is read again`() {
        val model = SessionsViewModel(retrofit.create(SessionsApi::class.java))
        until { !model.uiState.value.isLoading }
        assertEquals(listOf("s"), model.uiState.value.signins.map { it.hash })
        assertEquals(listOf("p"), model.uiState.value.passwords.map { it.hash })
        var closed = false
        model.revokeToken("s") { closed = true }
        until { closed && model.uiState.value.signins.isEmpty() && !model.uiState.value.isLoading }
        assertEquals("s", form("settings/-/user/account/token/revoke")["hash"])
    }

    @Test
    fun `a Matrix server key is replaced with the step-up's proof`() {
        val model = SystemStatusViewModel(
            retrofit.create(SystemStatusApi::class.java),
            retrofit.create(NetworksApi::class.java),
            SettingsStepUpClient(retrofit.create(AccountApi::class.java), PasskeyManager(context)),
        )
        until { !model.uiState.value.isLoading }
        assertEquals("ed25519:a", model.uiState.value.networks?.networks?.get("matrix")?.keys?.single()?.key)
        model.replaceServerKey("m.example")
        assertTrue(model.stepUp.visible.value)
        model.stepUp.onVerified("proof")
        until { requests.any { it.first == "settings/-/system/networks/key/replace" } && model.uiState.value.replacing == null }
        assertEquals(mapOf("domain" to "m.example", "token" to "proof"), form("settings/-/system/networks/key/replace"))
    }

    @Test
    fun `home lists Networks where the server offers them, and not where it does not`() {
        val model = SettingsHomeViewModel(retrofit.create(DomainsApi::class.java), retrofit.create(NetworksApi::class.java))
        until { model.state.value.hasNetworks && model.state.value.isAdmin }
        offered = false
        model.refresh()
        until { !model.state.value.hasNetworks }
    }
}
