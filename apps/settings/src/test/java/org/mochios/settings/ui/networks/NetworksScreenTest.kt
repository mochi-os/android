// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.networks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.UserPreferences
import org.mochios.settings.R
import org.mochios.settings.api.Availability
import org.mochios.settings.api.Handle
import org.mochios.settings.api.Handles
import org.mochios.settings.api.NetworkAccount
import org.mochios.settings.api.NetworkBlock
import org.mochios.settings.api.NetworkFailure
import org.mochios.settings.api.NetworkMute
import org.mochios.settings.api.NetworkProvider
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
        addresses = mapOf(
            "fediverse" to "@alice@a.example",
            "bluesky" to "alice.a.example",
            "matrix" to "@alice:a.example",
            "xmpp" to "alice@a.example",
        ),
    )
    private val hosted = NetworkAccount(id = "f1", type = "fediverse", hosted = true, identifier = "@alice@a.example")

    private fun content(
        data: NetworksData,
        busy: String? = null,
        onToggle: (Handle, String, NetworkAccount?) -> Unit = { _, _, _ -> },
        onArrive: (Handle) -> Unit = {},
        onLink: () -> Unit = {},
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
                onLink = onLink,
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
        val fediverse = rule.onNodeWithContentDescription(text(R.string.networks_network_for, "ActivityPub", "alice"))
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
    fun `Linked accounts shows where a provider links one or one is linked, and links only where a provider does`() {
        val linked = NetworkAccount(id = "m1", type = "mastodon", identifier = "@bob@m.example", server = "m.example")
        var shown by mutableStateOf(base)
        rule.setContent {
            NetworksContent(
                data = shown,
                busy = null,
                check = { name, domain -> Availability(available = true, name = name, domain = domain) },
                onReserve = { _, _, _ -> },
                onLink = {},
                onToggle = { _, _, _ -> },
                onAddName = {},
                onArrive = {},
                onRelease = {},
                actions = AccountActions(),
                onBlock = { _, _ -> },
                onUnblock = {},
                onUnmute = {},
            )
        }
        rule.onNodeWithText(text(R.string.networks_linked_accounts)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.networks_link)).assertDoesNotExist()

        shown = base.copy(providers = listOf(NetworkProvider(type = "mastodon")))
        rule.waitForIdle()
        rule.onNodeWithText(text(R.string.networks_linked_accounts)).assertExists()
        rule.onNodeWithText(text(R.string.networks_link)).assertExists()

        shown = base.copy(accounts = listOf(hosted, linked))
        rule.waitForIdle()
        rule.onNodeWithText(text(R.string.networks_linked_accounts)).assertExists()
        rule.onNodeWithText(text(R.string.networks_link)).assertDoesNotExist()
        rule.onNodeWithText("@bob@m.example").assertExists()
    }

    @Test
    fun `a hosted account's status and menu are in its handle row, and switching off is how it is removed`() {
        val acted = mutableListOf<String>()
        val bluesky = NetworkAccount(id = "b1", type = "bluesky", hosted = true, identifier = "alice.a.example")
        content(
            base.copy(accounts = listOf(bluesky)),
            actions = AccountActions(onKeys = { acted += "keys:${it.id}" }, onRemove = { acted += "remove:${it.id}" }),
        )
        val address = rule.onNodeWithText("alice.a.example").getBoundsInRoot()
        val switch = rule.onNodeWithContentDescription(text(R.string.networks_network_for, "Bluesky", "alice")).getBoundsInRoot()
        val status = rule.onNodeWithText(text(R.string.networks_no_recovery)).getBoundsInRoot()
        val menu = rule.onNodeWithContentDescription(text(R.string.networks_account_actions)).getBoundsInRoot()
        // The status sits under the row's address, and the menu beside the row's switch.
        assertTrue(status.top >= address.bottom && status.top <= switch.bottom + 24.dp)
        assertTrue(((menu.top + menu.bottom) / 2 - (switch.top + switch.bottom) / 2).value in -1f..1f)
        assertTrue(menu.right <= switch.left)
        rule.onNodeWithText(text(R.string.networks_linked_accounts)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.networks_communities)).assertDoesNotExist()

        rule.onNodeWithContentDescription(text(R.string.networks_account_actions)).performClick()
        rule.onNodeWithText(text(R.string.networks_profile)).assertExists()
        rule.onNodeWithText(text(R.string.networks_labelers)).assertExists()
        rule.onNodeWithText(text(R.string.networks_profile_show)).assertExists()
        rule.onNodeWithText(text(R.string.networks_remove)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.networks_keys)).performClick()
        assertEquals(listOf("keys:b1"), acted)
    }

    @Test
    fun `linked accounts are rows under Linked accounts, named for their network, whose header links another`() {
        var linking = 0
        val acted = mutableListOf<String>()
        val bob = NetworkAccount(id = "m1", type = "mastodon", identifier = "@bob@m.example", server = "m.example", name = "Bob")
        val zoe = NetworkAccount(id = "l1", type = "lemmy", identifier = "@zoe@l.example", server = "l.example")
        content(
            base.copy(accounts = listOf(zoe, hosted, bob), providers = listOf(NetworkProvider(type = "mastodon"))),
            onLink = { linking++ },
            actions = AccountActions(onTest = { acted += "test:${it.id}" }, onRemove = { acted += "remove:${it.id}" }),
        )
        val card = rule.onNodeWithContentDescription(text(R.string.networks_network_for, "Bluesky", "alice")).getBoundsInRoot()
        val title = rule.onNodeWithText(text(R.string.networks_linked_accounts)).getBoundsInRoot()
        val link = rule.onNodeWithText(text(R.string.networks_link)).getBoundsInRoot()
        // The Link action is in the section's header, beside its title, below the handle cards.
        assertTrue(title.top > card.bottom)
        assertTrue(link.top < title.bottom && link.bottom > title.top)
        val named = rule.onNodeWithText(text(R.string.networks_name_on, "Bob", "Mastodon")).getBoundsInRoot()
        val bare = rule.onNodeWithText("Lemmy").getBoundsInRoot()
        assertTrue(named.top > title.bottom)
        // In order of address.
        assertTrue(bare.top > named.bottom)
        rule.onNodeWithText("@bob@m.example").assertExists()
        rule.onNodeWithText("l.example").assertExists()
        rule.onNodeWithText(text(R.string.networks_link)).performClick()
        assertEquals(1, linking)

        // The hosted account's menu is on its card, then Bob's, then Zoe's.
        rule.onAllNodesWithContentDescription(text(R.string.networks_account_actions)).assertCountEquals(3)
        rule.onAllNodesWithContentDescription(text(R.string.networks_account_actions))[1].performClick()
        rule.onNodeWithText(text(R.string.networks_password_title)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.networks_profile)).assertExists()
        rule.onNodeWithText(text(R.string.networks_profile_show)).assertExists()
        rule.onNodeWithText(text(R.string.networks_test)).performClick()
        rule.onAllNodesWithContentDescription(text(R.string.networks_account_actions))[1].performClick()
        rule.onNodeWithText(text(R.string.networks_remove)).performScrollTo().performClick()
        assertEquals(listOf("test:m1", "remove:m1"), acted)
    }

    @Test
    fun `a hosted community, which no handle card shows, is listed under Communities and removed from its menu`() {
        val removed = mutableListOf<String>()
        val club = Handle(domain = "a.example", name = "books", kind = "community", addresses = mapOf("fediverse" to "@books@a.example"))
        val books = NetworkAccount(id = "g1", type = "fediverse", hosted = true, kind = "group", identifier = "@books@a.example", name = "Books")
        content(
            base.copy(handles = Handles(handles = listOf(alice, club), limit = 1, counted = 1), accounts = listOf(books, hosted)),
            actions = AccountActions(onRemove = { removed += it.id }),
        )
        val title = rule.onNodeWithText(text(R.string.networks_communities)).getBoundsInRoot()
        val row = rule.onNodeWithText(text(R.string.networks_name_on, "Books", "ActivityPub")).getBoundsInRoot()
        assertTrue(row.top > title.bottom)
        rule.onNodeWithText("@books@a.example").assertExists()
        rule.onNodeWithText(text(R.string.networks_linked_accounts)).assertDoesNotExist()

        // Alice's account's menu is on her card; the community's follows.
        rule.onAllNodesWithContentDescription(text(R.string.networks_account_actions)).assertCountEquals(2)
        rule.onAllNodesWithContentDescription(text(R.string.networks_account_actions))[1].performClick()
        rule.onNodeWithText(text(R.string.networks_password_title)).assertExists()
        rule.onNodeWithText(text(R.string.networks_home)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.networks_remove)).performScrollTo().performClick()
        assertEquals(listOf("g1"), removed)
    }

    @Test
    fun `a hosted account on a network the server no longer offers stays on its handle's card, switched on`() {
        val toggled = mutableListOf<String?>()
        val xmpp = NetworkAccount(id = "j1", type = "xmpp", hosted = true, identifier = "alice@a.example", failure = NetworkFailure("closed"))
        content(base.copy(accounts = listOf(hosted, xmpp)), onToggle = { _, _, account -> toggled += account?.id })
        val switch = rule.onNodeWithContentDescription(text(R.string.networks_network_for, "XMPP", "alice"))
        switch.assertIsOn()
        rule.onAllNodesWithText("alice@a.example").assertCountEquals(1)
        rule.onNodeWithText(text(R.string.networks_failure_closed)).assertExists()
        rule.onAllNodesWithContentDescription(text(R.string.networks_account_actions)).assertCountEquals(2)
        rule.onNodeWithText(text(R.string.networks_communities)).assertDoesNotExist()
        // A network nobody has an account on since it stopped being offered has no row.
        rule.onNodeWithContentDescription(text(R.string.networks_network_for, "Matrix", "alice")).assertDoesNotExist()
        switch.performClick()
        assertEquals(listOf("j1"), toggled)
    }

    @Test
    fun `each account appears once, with no Accounts section and no hosted or linked badge`() {
        val bluesky = NetworkAccount(id = "b1", type = "bluesky", hosted = true, identifier = "alice.a.example", recovery = true)
        val linked = NetworkAccount(id = "m1", type = "mastodon", identifier = "@bob@m.example", server = "m.example")
        val club = Handle(domain = "a.example", name = "books", kind = "community", addresses = mapOf("fediverse" to "@books@a.example"))
        val books = NetworkAccount(id = "g1", type = "fediverse", hosted = true, kind = "group", identifier = "@books@a.example")
        content(base.copy(handles = Handles(handles = listOf(alice, club), limit = 1, counted = 1), accounts = listOf(hosted, bluesky, linked, books)))
        for (address in listOf("@alice@a.example", "alice.a.example", "@bob@m.example", "@books@a.example")) {
            rule.onAllNodesWithText(address).assertCountEquals(1)
        }
        rule.onAllNodesWithContentDescription(text(R.string.networks_account_actions)).assertCountEquals(4)
        rule.onNodeWithText("Accounts").assertDoesNotExist()
        rule.onNodeWithText("Hosted").assertDoesNotExist()
        rule.onNodeWithText("Linked").assertDoesNotExist()
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
    fun `a linked account says why it is not working in words, and when it tries again`() {
        val retry = 1_793_196_300L
        val linked = NetworkAccount(id = "m1", type = "mastodon", identifier = "@alice@m.example", server = "m.example")
        val failing = linked.copy(failure = NetworkFailure("status", 504, retry), error = "the server answered 504: failed to WebSocket dial")
        content(base.copy(accounts = listOf(failing)))
        val time = Format(UserPreferences()).formatTime(retry)
        rule.onNodeWithText(text(R.string.networks_failure_status, "504") + "\n" + text(R.string.networks_failure_retry, time)).assertExists()
        rule.onNodeWithText("WebSocket", substring = true).assertDoesNotExist()
    }

    @Test
    fun `each reason a linked account is not working has its words, with no time while it tries`() {
        val linked = NetworkAccount(id = "m1", type = "mastodon", identifier = "@alice@m.example", server = "m.example", error = "the raw error")
        val worded = listOf(
            "unreachable" to R.string.networks_failure_unreachable,
            "closed" to R.string.networks_failure_closed,
            "blocked" to R.string.networks_failure_blocked,
            "signed_out" to R.string.networks_failure_signed_out,
            "failed" to R.string.networks_failure_failed,
            "unknown" to R.string.networks_failure_failed,
        )
        var shown by mutableStateOf(base)
        rule.setContent {
            NetworksContent(
                data = shown,
                busy = null,
                check = { name, domain -> Availability(available = true, name = name, domain = domain) },
                onReserve = { _, _, _ -> },
                onLink = {},
                onToggle = { _, _, _ -> },
                onAddName = {},
                onArrive = {},
                onRelease = {},
                actions = AccountActions(),
                onBlock = { _, _ -> },
                onUnblock = {},
                onUnmute = {},
            )
        }
        for ((reason, id) in worded) {
            shown = base.copy(accounts = listOf(linked.copy(failure = NetworkFailure(reason))))
            rule.waitForIdle()
            rule.onNodeWithText(text(id)).assertExists()
            rule.onNodeWithText("the raw error").assertDoesNotExist()
        }
        // A server too old to say why answers only the text.
        shown = base.copy(accounts = listOf(linked))
        rule.waitForIdle()
        rule.onNodeWithText("the raw error").assertExists()
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
    fun `a hosted fediverse account's menu offers its keys`() {
        val fediverse = NetworkAccount(id = "f1", type = "fediverse", hosted = true, identifier = "@alice@a.example")
        content(base.copy(accounts = listOf(fediverse)))
        rule.onNodeWithContentDescription(text(R.string.networks_account_actions)).performClick()
        rule.onNodeWithText(text(R.string.networks_keys)).assertExists()
    }

    @Test
    fun `a hosted Matrix account's menu offers no keys, which it has none of`() {
        val matrix = NetworkAccount(id = "x1", type = "matrix", hosted = true, identifier = "@alice:a.example")
        content(base.copy(networks = listOf("matrix"), accounts = listOf(matrix)))
        rule.onNodeWithContentDescription(text(R.string.networks_account_actions)).performClick()
        rule.onNodeWithText(text(R.string.networks_profile)).assertExists()
        rule.onNodeWithText(text(R.string.networks_keys)).assertDoesNotExist()
    }

    @Test
    fun `a hosted XMPP account's menu offers no keys, which it has none of`() {
        val xmpp = NetworkAccount(id = "j1", type = "xmpp", hosted = true, identifier = "alice@a.example")
        content(base.copy(networks = listOf("xmpp"), accounts = listOf(xmpp)))
        rule.onNodeWithContentDescription(text(R.string.networks_account_actions)).performClick()
        rule.onNodeWithText(text(R.string.networks_profile)).assertExists()
        rule.onNodeWithText(text(R.string.networks_keys)).assertDoesNotExist()
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
