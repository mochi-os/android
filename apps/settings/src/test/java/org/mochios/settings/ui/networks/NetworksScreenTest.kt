// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.networks

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.settings.R
import org.mochios.settings.api.Availability
import org.mochios.settings.api.Handle
import org.mochios.settings.api.Handles
import org.mochios.settings.api.NetworkAccount
import org.mochios.settings.api.NetworkBlock
import org.mochios.settings.api.NetworkMute
import org.mochios.settings.api.NetworksData
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The Networks screen's cards, sections and dialogs, as the web page shows them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h1600dp")
class NetworksScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun text(id: Int, vararg args: Any): String = RuntimeEnvironment.getApplication().getString(id, *args)

    private val alice = Handle(
        domain = "a.example",
        name = "alice",
        kind = "handle",
        addresses = mapOf("fediverse" to "@alice@a.example", "bluesky" to "alice.a.example"),
    )
    private val hosted = NetworkAccount(id = "f1", type = "fediverse", hosted = true, identifier = "@alice@a.example")

    private fun content(
        data: NetworksData,
        busy: String? = null,
        onToggle: (Handle, String, NetworkAccount?) -> Unit = { _, _, _ -> },
        onArrive: (Handle) -> Unit = {},
        onUnblock: (String) -> Unit = {},
        onUnmute: (String) -> Unit = {},
        actions: AccountActions = AccountActions(),
    ) {
        rule.setContent {
            NetworksContent(
                data = data,
                busy = busy,
                check = { name, domain -> Availability(available = true, name = name, domain = domain) },
                onReserve = { _, _, _ -> },
                onLink = {},
                onToggle = onToggle,
                onAddName = {},
                onArrive = onArrive,
                onRelease = {},
                actions = actions,
                onBlock = { _, _ -> },
                onUnblock = onUnblock,
                onUnmute = onUnmute,
            )
        }
    }

    private val base = NetworksData(
        offered = true,
        networks = listOf("fediverse", "bluesky"),
        domains = listOf("a.example"),
        handles = Handles(handles = listOf(alice), limit = 1, counted = 1),
        accounts = listOf(hosted),
    )

    @Test
    fun `a handle's card has a switch per network, on where the hosted account exists`() {
        val toggled = mutableListOf<Pair<String, String?>>()
        content(base, onToggle = { _, network, account -> toggled += network to account?.id })
        // Each row names the address switching the network on makes.
        rule.onNodeWithText("alice.a.example").assertExists()
        val fediverse = rule.onNodeWithContentDescription(text(R.string.networks_network_for, text(R.string.networks_fediverse), "alice"))
        val bluesky = rule.onNodeWithContentDescription(text(R.string.networks_network_for, "Bluesky", "alice"))
        fediverse.assertIsOn()
        bluesky.assertIsOff()
        fediverse.performClick()
        bluesky.performClick()
        assertEquals(listOf("fediverse" to "f1", "bluesky" to null), toggled)
    }

    @Test
    fun `a network being switched on for a name cannot be switched again until it answers`() {
        content(base, busy = "alice/bluesky")
        rule.onNodeWithContentDescription(text(R.string.networks_network_for, "Bluesky", "alice")).assertIsNotEnabled()
    }

    @Test
    fun `a Bluesky account can be moved here only to a handle without one`() {
        val arrived = mutableListOf<String>()
        content(base, onArrive = { arrived += it.name })
        rule.onNodeWithContentDescription(text(R.string.networks_handle_actions)).performClick()
        rule.onNodeWithText(text(R.string.networks_arrive)).performClick()
        assertEquals(listOf("alice"), arrived)
    }

    @Test
    fun `a handle with its Bluesky account offers no move here`() {
        val bluesky = NetworkAccount(id = "b1", type = "bluesky", hosted = true, identifier = "alice.a.example", recovery = true)
        content(base.copy(accounts = listOf(hosted, bluesky)))
        rule.onNodeWithContentDescription(text(R.string.networks_handle_actions)).performClick()
        rule.onNodeWithText(text(R.string.networks_add_a_name)).assertExists()
        rule.onNodeWithText(text(R.string.networks_arrive)).assertDoesNotExist()
    }

    @Test
    fun `the chooser shows only while a handle may be reserved, suggested from the name before the first`() {
        content(base.copy(handles = Handles(limit = 1, counted = 0), name = "José Álvarez"))
        rule.onNodeWithText(text(R.string.networks_choose)).assertExists()
        rule.onNode(hasSetTextAction() and androidx.compose.ui.test.hasText("josealvarez")).assertExists()
    }

    @Test
    fun `no chooser once the limit is reached`() {
        content(base)
        rule.onNodeWithText(text(R.string.networks_choose)).assertDoesNotExist()
    }

    @Test
    fun `an account is linked from the screen only where a provider links one`() {
        content(base)
        rule.onNodeWithText(text(R.string.networks_link)).assertDoesNotExist()
    }

    @Test
    fun `a hosted fediverse account's menu has its keys, aliases, move and the automated mark`() {
        val acted = mutableListOf<String>()
        content(
            base,
            actions = AccountActions(onAliases = { acted += "aliases" }, onKeys = { acted += "keys" }),
        )
        rule.onNodeWithContentDescription(text(R.string.networks_account_actions)).performClick()
        rule.onNodeWithText(text(R.string.networks_password_title)).assertExists()
        rule.onNodeWithText(text(R.string.networks_home)).assertExists()
        rule.onNodeWithText(text(R.string.networks_mark_automated)).assertExists()
        rule.onNodeWithText(text(R.string.networks_move)).assertExists()
        rule.onNodeWithText(text(R.string.networks_test)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.networks_labelers)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.networks_aliases)).performClick()
        assertEquals(listOf("aliases"), acted)
    }

    @Test
    fun `a moved account offers no second move`() {
        content(base.copy(accounts = listOf(hosted.copy(moved = "@alice@b.example"))))
        rule.onNodeWithText(text(R.string.networks_moved_to, "@alice@b.example")).assertExists()
        rule.onNodeWithContentDescription(text(R.string.networks_account_actions)).performClick()
        rule.onNodeWithText(text(R.string.networks_aliases)).assertExists()
        rule.onNodeWithText(text(R.string.networks_move)).assertDoesNotExist()
    }

    @Test
    fun `a hosted Bluesky account names its missing recovery key and reads labellers`() {
        val bluesky = NetworkAccount(id = "b1", type = "bluesky", hosted = true, identifier = "alice.a.example")
        content(base.copy(accounts = listOf(bluesky)))
        rule.onNodeWithText(text(R.string.networks_no_recovery)).assertExists()
        rule.onNodeWithContentDescription(text(R.string.networks_account_actions)).performClick()
        rule.onNodeWithText(text(R.string.networks_labelers)).assertExists()
        rule.onNodeWithText(text(R.string.networks_keys)).assertExists()
        rule.onNodeWithText(text(R.string.networks_aliases)).assertDoesNotExist()
    }

    @Test
    fun `a linked account's menu tests it`() {
        val linked = NetworkAccount(id = "m1", type = "mastodon", identifier = "@bob@m.example", server = "m.example")
        val tested = mutableListOf<String>()
        content(base.copy(accounts = listOf(linked)), actions = AccountActions(onTest = { tested += it.id }))
        rule.onNodeWithText("m.example").assertExists()
        rule.onNodeWithContentDescription(text(R.string.networks_account_actions)).performClick()
        rule.onNodeWithText(text(R.string.networks_keys)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.networks_test)).performClick()
        assertEquals(listOf("m1"), tested)
    }

    @Test
    fun `what is blocked and muted is listed, and each is lifted by its own kind`() {
        val unblocked = mutableListOf<String>()
        val unmuted = mutableListOf<String>()
        content(
            base.copy(blocks = listOf(NetworkBlock(domain = "spam.example")), mutes = listOf(NetworkMute(actor = "https://m.example/users/x"))),
            onUnblock = { unblocked += it },
            onUnmute = { unmuted += it },
        )
        rule.onNodeWithContentDescription(text(R.string.networks_unblock, "spam.example")).performClick()
        rule.onNodeWithContentDescription(text(R.string.networks_unmute, "https://m.example/users/x")).performClick()
        assertEquals(listOf("spam.example"), unblocked)
        assertEquals(listOf("https://m.example/users/x"), unmuted)
    }
}
