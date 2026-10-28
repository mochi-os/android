// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.networks

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.settings.R
import org.mochios.settings.api.Budget
import org.mochios.settings.api.Certificates
import org.mochios.settings.api.NetworkState
import org.mochios.settings.api.OtherToken
import org.mochios.settings.api.RegistryAccount
import org.mochios.settings.api.RegistryHandle
import org.mochios.settings.api.RelayStanding
import org.mochios.settings.api.RoomService
import org.mochios.settings.api.ServerKey
import org.mochios.settings.api.SystemNetworks
import org.mochios.settings.api.SystemSetting
import org.mochios.settings.api.TokenClient
import org.mochios.settings.api.Usage
import org.mochios.settings.ui.handles.RegistryCard
import org.mochios.settings.ui.handles.SystemHandlesList
import org.mochios.settings.ui.handles.SystemHandlesUiState
import org.mochios.settings.ui.home.SettingsHomeScreen
import org.mochios.settings.ui.sessions.TokenRow
import org.mochios.settings.ui.systemsettings.SystemSettingsContent
import org.mochios.settings.ui.systemsettings.SystemSettingsUiState
import org.mochios.settings.ui.systemstatus.OtherNetworksSections
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The sign-ins, the owner's status sections and handles, and where the screens are reached from. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h1600dp")
class OwnerScreensTest {

    @get:Rule
    val rule = createComposeRule()

    private fun text(id: Int, vararg args: Any): String = RuntimeEnvironment.getApplication().getString(id, *args)

    @Test
    fun `a sign-in names its app and device, and revoking it asks first, naming the account`() {
        val revoked = mutableListOf<String>()
        val token = OtherToken(
            hash = "h1",
            kind = "signin",
            name = "token",
            client = TokenClient(name = "Ivory"),
            device = "Phone",
            address = "@alice@a.example",
            network = "fediverse",
        )
        rule.setContent { TokenRow(token = token, revoking = false, onRevoke = { revoked += token.hash }) }
        rule.onNodeWithText("Ivory").assertExists()
        rule.onNodeWithText("Phone").assertExists()
        rule.onNodeWithText("@alice@a.example · " + text(R.string.networks_fediverse)).assertExists()
        rule.onNodeWithText(text(R.string.account_last_used, text(R.string.sessions_never))).assertExists()
        rule.onNodeWithContentDescription(text(R.string.sessions_revoke)).performClick()
        rule.onNodeWithText(text(R.string.sessions_revoke_signin_message, "Ivory", "@alice@a.example")).assertExists()
        rule.onNodeWithText(text(R.string.sessions_revoke)).performClick()
        assertEquals(listOf("h1"), revoked)
    }

    @Test
    fun `a password is named by its device`() {
        val token = OtherToken(hash = "h2", kind = "password", device = "Laptop", address = "alice@a.example", network = "xmpp")
        rule.setContent { TokenRow(token = token, revoking = false, onRevoke = {}) }
        rule.onNodeWithText("Laptop").assertExists()
        rule.onNodeWithContentDescription(text(R.string.sessions_revoke)).performClick()
        rule.onNodeWithText(text(R.string.sessions_revoke_password_title)).assertExists()
    }

    private val networks = SystemNetworks(
        development = true,
        networks = mapOf(
            "matrix" to NetworkState(state = "working", keys = listOf(ServerKey(domain = "m.example", key = "ed25519:abc"))),
            "fediverse" to NetworkState(state = "unavailable", reason = "domain"),
            "bluesky" to NetworkState(
                state = "working",
                certificates = Certificates(issued = 3, limit = 50),
                budget = Budget(hour = Usage(1, 150), day = Usage(20, 900)),
                relay = RelayStanding(status = "active", accounts = 2, limit = 10),
            ),
            "xmpp" to NetworkState(state = "disabled", rooms = listOf(RoomService(host = "rooms.x.example", state = "unavailable"))),
        ),
    )

    @Test
    fun `each network's state is told in words, with what it has spent`() {
        rule.setContent { OtherNetworksSections(networks = networks, replacing = null, onReplace = {}) }
        rule.onNodeWithText(text(R.string.system_status_other_networks)).assertExists()
        rule.onNodeWithText(text(R.string.system_status_unavailable, text(R.string.system_status_reason_domain))).assertExists()
        rule.onNodeWithText(text(R.string.system_status_disabled)).assertExists()
        rule.onNodeWithText(
            listOf(
                text(R.string.system_status_working),
                text(R.string.system_status_certificates, "3", "50"),
                text(R.string.system_status_budget, "1", "150", "20", "900"),
                text(R.string.system_status_relay_active) + ", " + text(R.string.system_status_relay_accounts, "2", "10"),
            ).joinToString(" · "),
        ).assertExists()
        rule.onNodeWithText(text(R.string.system_status_rooms_unreachable)).assertExists()
    }

    @Test
    fun `a Matrix server key is replaced only once the owner confirms`() {
        val replaced = mutableListOf<String>()
        rule.setContent { OtherNetworksSections(networks = networks, replacing = null, onReplace = { replaced += it }) }
        rule.onNodeWithText("ed25519:abc").assertExists()
        rule.onNodeWithText(text(R.string.system_status_replace)).performClick()
        assertEquals(emptyList<String>(), replaced)
        rule.onNodeWithText(text(R.string.system_status_key_title, "m.example")).assertExists()
        rule.onAllNodes(androidx.compose.ui.test.hasText(text(R.string.system_status_replace)))[1].performClick()
        assertEquals(listOf("m.example"), replaced)
    }

    @Test
    fun `nothing of the other networks shows while the server does not offer them`() {
        rule.setContent { OtherNetworksSections(networks = networks.copy(development = false), replacing = null, onReplace = {}) }
        rule.onNodeWithText(text(R.string.system_status_other_networks)).assertDoesNotExist()
    }

    @Test
    fun `the owner takes a hosted Bluesky account down from its handle, and restores one taken down`() {
        val down = mutableListOf<String>()
        val restored = mutableListOf<String>()
        val handle = RegistryHandle(
            domain = "a.example",
            name = "alice",
            username = "alice@example.com",
            holder = "Alice",
            // Out of order, so the card's own sort shows.
            accounts = listOf(
                RegistryAccount(id = "f1", network = "fediverse", identifier = "@alice@a.example"),
                RegistryAccount(id = "b1", network = "bluesky", identifier = "alice.a.example", did = "did:plc:1"),
                RegistryAccount(id = "b2", network = "bluesky", identifier = "news.a.example", did = "did:plc:2", status = "takendown"),
            ),
        )
        rule.setContent {
            RegistryCard(handle = handle, busy = null, onTakedown = { down += it.id }, onRestore = { restored += it.id }, onRelease = {})
        }
        rule.onNodeWithText("Alice · alice@example.com").assertExists()
        // The networks by name, not the addresses; the taken-down one says so.
        rule.onAllNodesWithText("Bluesky").assertCountEquals(2)
        rule.onAllNodesWithText("Fediverse").assertCountEquals(1)
        rule.onNodeWithText("@alice@a.example").assertDoesNotExist()
        rule.onNodeWithText("news.a.example").assertDoesNotExist()
        val gone = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text(R.string.handles_taken_down))
        rule.onAllNodes(hasText("Bluesky") and gone).assertCountEquals(1)
        rule.onAllNodes(hasText("Fediverse") and gone).assertCountEquals(0)
        // Sorted by network name: Bluesky, Bluesky, Fediverse.
        val names = rule.onAllNodes(hasText("Bluesky") or hasText("Fediverse")).fetchSemanticsNodes()
            .sortedBy { it.positionInRoot.x }
            .map { it.config[SemanticsProperties.Text].joinToString() }
        assertEquals(listOf("Bluesky", "Bluesky", "Fediverse"), names)
        rule.onNodeWithContentDescription(text(R.string.networks_handle_actions)).performClick()
        rule.onNodeWithText(text(R.string.handles_take_down, "@alice@a.example")).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.handles_take_down, "alice.a.example")).performClick()
        rule.onNodeWithContentDescription(text(R.string.networks_handle_actions)).performClick()
        rule.onNodeWithText(text(R.string.handles_restore, "news.a.example")).performClick()
        assertEquals(listOf("b1"), down)
        assertEquals(listOf("b2"), restored)
    }

    private fun handles(state: SystemHandlesUiState, next: () -> Unit = {}) {
        rule.setContent {
            SystemHandlesList(
                state = state,
                onRetry = {},
                check = { _, _ -> null },
                onReserve = { _, _, _ -> },
                onTakedown = {},
                onRestore = {},
                onRelease = {},
                onLimit = {},
                onPrevious = {},
                onNext = next,
            )
        }
    }

    @Test
    fun `the handles are paged, the line naming handles`() {
        var nexted = 0
        // A short page, so the lazy list composes the pages line too.
        val page = (0 until 2).map { RegistryHandle(domain = "a.example", name = "name$it", username = "u@a.example") }
        handles(SystemHandlesUiState(isLoading = false, handles = page, count = 60, limit = 25, offset = 0), next = { nexted++ })
        val line = RuntimeEnvironment.getApplication().resources
            .getQuantityString(R.plurals.handles_pagination_status, 60, "1", "25", "60")
        assertEquals("Showing 1–25 of 60 handles", line)
        rule.onNodeWithText(line).performScrollTo().assertExists()
        rule.onNodeWithText(text(R.string.system_users_next)).performScrollTo().performClick()
        assertEquals(1, nexted)
    }

    @Test
    fun `one page of handles has no pages`() {
        handles(SystemHandlesUiState(isLoading = false, handles = listOf(RegistryHandle(domain = "a.example", name = "alice")), count = 1))
        rule.onNodeWithText(text(R.string.system_users_next)).assertDoesNotExist()
    }

    @Test
    fun `an empty search says nothing matches, an empty registry that nothing is reserved`() {
        handles(SystemHandlesUiState(isLoading = false, search = "zzz"))
        rule.onNodeWithText(text(R.string.handles_empty_search)).assertExists()
        rule.onNodeWithText(text(R.string.handles_empty)).assertDoesNotExist()
    }

    @Test
    fun `an empty registry says nothing is reserved`() {
        handles(SystemHandlesUiState(isLoading = false))
        rule.onNodeWithText(text(R.string.handles_empty)).assertExists()
        rule.onNodeWithText(text(R.string.handles_empty_search)).assertDoesNotExist()
    }

    @Test
    fun `the network settings are a section of their own, the description with the operator's`() {
        val settings = listOf("handles", "fediverse", "description", "operator_name").map { SystemSetting(name = it, value = "x") }
        rule.setContent { SystemSettingsContent(state = SystemSettingsUiState(isLoading = false, settings = settings), onSave = { _, _ -> }) }
        rule.onNodeWithText(text(R.string.system_status_other_networks)).performScrollTo().assertExists()
        rule.onNodeWithText(text(R.string.system_setting_handles)).performScrollTo().assertExists()
        rule.onNodeWithText(text(R.string.system_setting_fediverse)).performScrollTo().assertExists()
        rule.onNodeWithText(text(R.string.system_setting_description)).performScrollTo().assertExists()
    }

    @Composable
    private fun home(isAdmin: Boolean, hasNetworks: Boolean, opened: MutableList<String>) {
        SettingsHomeScreen(
            isAdmin = isAdmin,
            hasDomainAccess = false,
            hasNetworks = hasNetworks,
            onOpenAccount = {},
            onOpenLogin = {},
            onOpenPreferences = {},
            onOpenDisplay = {},
            onOpenNotifications = {},
            onOpenNotificationPrefs = {},
            onOpenSessions = {},
            onOpenSystemSettings = {},
            onOpenSystemStatus = {},
            onOpenSystemUsers = {},
            onOpenInterests = {},
            onOpenAccounts = {},
            onOpenDomains = {},
            onOpenDocument = {},
            onOpenSystemDocuments = {},
            onOpenNetworks = { opened += "networks" },
            onOpenSystemHandles = { opened += "handles" },
            onOpenSystemReports = { opened += "reports" },
            onOpenSystemBlocks = { opened += "blocks" },
            onOpenSystemFederation = { opened += "federation" },
        )
    }

    @Test
    fun `Networks and the owner's Handles are reached from home where the server offers networks`() {
        val opened = mutableListOf<String>()
        rule.setContent { home(isAdmin = true, hasNetworks = true, opened = opened) }
        rule.onNodeWithText(text(R.string.networks_title)).performClick()
        rule.onNodeWithText(text(R.string.handles_title)).performScrollTo().performClick()
        rule.onNodeWithText(text(R.string.reports_title)).performScrollTo().performClick()
        rule.onNodeWithText(text(R.string.blocks_title)).performScrollTo().performClick()
        rule.onNodeWithText(text(R.string.federation_title)).performScrollTo().performClick()
        assertEquals(listOf("networks", "handles", "reports", "blocks", "federation"), opened)
    }

    @Test
    fun `neither is listed where the server offers no networks, nor Handles to a user`() {
        rule.setContent { home(isAdmin = true, hasNetworks = false, opened = mutableListOf()) }
        rule.onNodeWithText(text(R.string.networks_title)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.handles_title)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.reports_title)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.blocks_title)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.federation_title)).assertDoesNotExist()
    }

    @Test
    fun `a user who is not an administrator sees Networks but not Handles`() {
        rule.setContent { home(isAdmin = false, hasNetworks = true, opened = mutableListOf()) }
        rule.onNodeWithText(text(R.string.networks_title)).assertExists()
        rule.onNodeWithText(text(R.string.handles_title)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.reports_title)).assertDoesNotExist()
    }
}
