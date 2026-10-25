// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.moderation

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
import org.mochios.settings.api.BlockedDomain
import org.mochios.settings.api.BlockedPerson
import org.mochios.settings.api.FediverseHost
import org.mochios.settings.api.ModerationApi
import org.mochios.settings.api.Report
import org.mochios.settings.api.ReportAccount
import org.mochios.settings.api.ReportParty
import org.mochios.settings.api.SystemBlocks
import org.mochios.settings.api.SystemHosts
import org.mochios.settings.api.XmppHealth
import org.mochios.settings.api.XmppLink
import org.mochios.settings.ui.login.SettingsStepUpClient
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/** The owner's Reports, Blocks and Federation screens (the federation plan, phase 40). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModerationTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private val requests = CopyOnWriteArrayList<Pair<String, String>>()

    private val flag = Report(
        id = "r1",
        network = "fediverse",
        reporter = ReportParty(id = "https://remote.example/actor", name = "remote.example", blockable = true, host = "remote.example"),
        person = ReportParty(id = "https://example.com/~f1", name = "@alice@example.com", blockable = false, host = ""),
        user = "u-alice",
        account = ReportAccount(username = "alice@example.com", status = "active"),
    )

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().removePrefix("/")
                requests += path to request.body.readUtf8()
                return when (path.substringBefore("?")) {
                    "settings/-/system/reports/list" -> json(
                        """{"reports": [{"id": "r1", "network": "fediverse", "reporter": {"id": "https://remote.example/actor", "name": "remote.example", "blockable": true, "host": "remote.example"},
                           "person": null, "room": {"id": "!r:remote.example", "name": "Lobby"}, "objects": [], "created": 1700000000, "resolved": 0, "outcome": ""}]}""",
                    )
                    "settings/-/system/reports/resolve" -> json("""{"resolved": true}""")
                    "settings/-/system/blocks/list" -> json("""{"domains": [{"domain": "spam.example", "created": 1}], "people": []}""")
                    "settings/-/system/blocks/add", "settings/-/system/blocks/remove" -> json(
                        """{"domains": [{"domain": "spam.example", "created": 1}], "people": [{"actor": "xmpp:troll@chat.example", "created": 2}]}""",
                    )
                    "settings/-/system/hosts" -> json(
                        """{"fediverse": [{"host": "z.example", "limited": true}, {"host": "a.example"}], "matrix": [],
                           "xmpp": {"links": [], "sessions": {"clients": 3, "accounts": 1}}}""",
                    )
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

    private val api by lazy { retrofit.create(ModerationApi::class.java) }

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
    fun `a report offers the reporter's server and a suspension, never a block of someone here`() {
        assertEquals(
            listOf(ReportAction.Server("remote.example"), ReportAction.Suspend("alice@example.com")),
            reportActions(flag),
        )
    }

    @Test
    fun `a report about someone elsewhere offers them and their server, a suspended user no suspension`() {
        val remote = flag.copy(
            reporter = ReportParty(id = "https://example.com/~f1", name = "@alice@example.com"),
            person = ReportParty(id = "https://spam.example/u/bot", name = "@bot@spam.example", blockable = true, host = "spam.example"),
            account = ReportAccount(username = "bot", status = "suspended"),
        )
        assertEquals(
            listOf(ReportAction.Person("https://spam.example/u/bot", "@bot@spam.example"), ReportAction.Server("spam.example")),
            reportActions(remote),
        )
        // One server, both parties on it, is offered once.
        assertEquals(
            listOf(ReportAction.Server("remote.example")),
            reportActions(flag.copy(person = flag.reporter.copy(blockable = false), account = null)),
        )
    }

    @Test
    fun `blocks list servers and people by what they name, an XMPP address shown without its scheme`() {
        val rows = blockRows(SystemBlocks(domains = listOf(BlockedDomain("spam.example", 1)), people = listOf(BlockedPerson("xmpp:troll@chat.example", 2), BlockedPerson("@bot:matrix.example", 3))))
        assertEquals(listOf("@bot:matrix.example", "spam.example", "xmpp:troll@chat.example"), rows.map { it.key })
        assertEquals(listOf(true, false, true), rows.map { it.person })
        assertEquals("troll@chat.example", rows.last().label)
    }

    @Test
    fun `servers are listed by name`() {
        val hosts = hostsOrdered(SystemHosts(fediverse = listOf(FediverseHost("z.example"), FediverseHost("a.example")), xmpp = XmppHealth(links = listOf(XmppLink("b.example", "x.example"), XmppLink("b.example", "a.example")))))
        assertEquals(listOf("a.example", "z.example"), hosts.fediverse.map { it.host })
        assertEquals(listOf("a.example", "x.example"), hosts.xmpp.links.map { it.domain })
    }

    @Test
    fun `reports are read by state, dismissed, blocked by their server and suspended with the step-up's proof`() {
        val model = SystemReportsViewModel(api, SettingsStepUpClient(retrofit.create(AccountApi::class.java), PasskeyManager(context)))
        until { !model.uiState.value.isLoading }
        assertEquals("Lobby", model.uiState.value.reports.single().room?.name)
        assertTrue(requests.any { it.first == "settings/-/system/reports/list?state=open" })
        model.show(ReportState.RESOLVED)
        until { requests.any { it.first == "settings/-/system/reports/list?state=resolved" } && !model.uiState.value.isLoading }

        model.dismiss(flag)
        until { requests.any { it.first == "settings/-/system/reports/resolve" } && model.uiState.value.busy == null }
        assertEquals(mapOf("id" to "r1", "outcome" to "dismissed"), form("settings/-/system/reports/resolve"))

        model.act(flag, ReportAction.Server("remote.example"))
        until { requests.count { it.first == "settings/-/system/reports/resolve" } == 2 && model.uiState.value.busy == null }
        assertEquals(mapOf("id" to "r1", "outcome" to "blocked", "target" to "remote.example"), form("settings/-/system/reports/resolve"))

        model.act(flag, ReportAction.Suspend("alice@example.com"))
        assertTrue(model.stepUp.visible.value)
        assertEquals(2, requests.count { it.first == "settings/-/system/reports/resolve" })
        model.stepUp.onVerified("proof")
        until { requests.count { it.first == "settings/-/system/reports/resolve" } == 3 && model.uiState.value.busy == null }
        assertEquals(mapOf("id" to "r1", "outcome" to "suspended", "token" to "proof"), form("settings/-/system/reports/resolve"))
    }

    @Test
    fun `a block is added and lifted by its full address, and the list is the server's answer`() {
        val model = SystemBlocksViewModel(api)
        until { !model.uiState.value.isLoading }
        assertEquals(listOf("spam.example"), model.uiState.value.rows.map { it.key })
        var cleared = false
        model.block(" troll@chat.example ") { cleared = true }
        until { cleared }
        assertEquals(mapOf("target" to "troll@chat.example"), form("settings/-/system/blocks/add"))
        assertEquals(listOf("spam.example", "xmpp:troll@chat.example"), model.uiState.value.rows.map { it.key })
        model.unblock(model.uiState.value.rows.last())
        until { requests.any { it.first == "settings/-/system/blocks/remove" } && model.uiState.value.busy == null }
        assertEquals(mapOf("target" to "xmpp:troll@chat.example"), form("settings/-/system/blocks/remove"))
    }

    @Test
    fun `federation reads the servers in order`() {
        val model = SystemFederationViewModel(api)
        until { model.uiState.value.hosts != null }
        val hosts = model.uiState.value.hosts!!
        assertEquals(listOf("a.example", "z.example"), hosts.fediverse.map { it.host })
        assertTrue(hosts.fediverse.last().limited)
        assertEquals(3L, hosts.xmpp.sessions.clients)
    }
}
