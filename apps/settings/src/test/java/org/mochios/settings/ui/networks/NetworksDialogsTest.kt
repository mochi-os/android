// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.networks

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.settings.R
import org.mochios.settings.api.Availability
import org.mochios.settings.api.AccountKey
import org.mochios.settings.api.Handle
import org.mochios.settings.api.NetworkAccount
import org.mochios.settings.api.NetworkFeed
import org.mochios.settings.api.NetworkLink
import org.mochios.settings.api.NetworkProvider
import org.mochios.settings.api.ProviderField
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The Networks screen's dialogs with fields. Plain @Config: a MochiAlertDialog
 * with text fields never idles under display qualifiers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NetworksDialogsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun text(id: Int, vararg args: Any): String = RuntimeEnvironment.getApplication().getString(id, *args)

    private val alice = Handle(domain = "a.example", name = "alice", kind = "handle")

    @Test
    fun `linking by a server's consent continues, by a form it links, each with its fields`() {
        val providers = listOf(
            NetworkProvider(type = "mastodon", link = NetworkLink(flow = "oauth", fields = listOf(ProviderField(name = "server", label = "Server URL", required = true)))),
            NetworkProvider(type = "lemmy", link = NetworkLink(flow = "form", fields = listOf(ProviderField(name = "server", label = "Server URL", required = true)))),
        )
        val linked = mutableListOf<Triple<String, Map<String, String>, Boolean>>()
        rule.setContent {
            LinkDialog(providers = providers, busy = false, onLink = { type, fields, oauth -> linked += Triple(type, fields, oauth) }, onDismiss = {})
        }
        rule.onNodeWithText("Server URL").performTextInput("mastodon.social")
        rule.onNodeWithText(text(org.mochios.android.R.string.common_continue)).performClick()
        assertEquals(listOf(Triple("mastodon", mapOf("server" to "mastodon.social"), true)), linked)
    }

    @Test
    fun `a name under a handle is offered once the server says it is free`() {
        val added = mutableListOf<String>()
        rule.setContent {
            AddNameDialog(
                parent = alice,
                busy = false,
                check = { name, domain -> Availability(available = name == "alice_news", name = name, domain = domain) },
                onAdd = { added += it },
                onDismiss = {},
            )
        }
        rule.onNode(hasSetTextAction()).performTextInput("News!")
        rule.waitUntil(5000) { rule.onAllNodes(androidx.compose.ui.test.hasText(text(R.string.networks_available))).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(text(org.mochios.android.R.string.common_add)).performClick()
        assertEquals(listOf("alice_news"), added)
    }

    private fun common(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    @Test
    fun `a list's entry is removed or added by saving the whole list`() {
        val saved = mutableListOf<List<String>>()
        rule.setContent {
            ListDialog(
                title = "Aliases",
                initial = listOf("@a@b.example"),
                field = "Account elsewhere",
                placeholder = "",
                busy = false,
                onSave = { list, done -> saved += list; done() },
                onDismiss = {},
            )
        }
        rule.onNodeWithContentDescription(text(R.string.networks_remove_entry, "@a@b.example")).performClick()
        rule.onNode(hasSetTextAction()).performTextInput(" @c@d.example ")
        rule.onNodeWithText(common(org.mochios.android.R.string.common_add)).performClick()
        assertEquals(listOf(emptyList(), listOf("@c@d.example")), saved)
    }

    @Test
    fun `a home feed is chosen from the public feeds, or made anew`() {
        val chosen = mutableListOf<String>()
        val account = NetworkAccount(id = "f1", type = "fediverse", hosted = true, home = "private")
        val feeds = listOf(NetworkFeed(id = "p", name = "Notes", privacy = "public"), NetworkFeed(id = "private", name = "Diary", privacy = "private"))
        rule.setContent { HomeDialog(account = account, feeds = feeds, busy = false, onSave = { chosen += it }, onDismiss = {}) }
        // A private home feed is not one of the choices: the field reads None.
        rule.onNodeWithText(text(R.string.networks_home_none)).performClick()
        rule.onNodeWithText("Diary").assertDoesNotExist()
        rule.onNodeWithText(text(R.string.networks_home_new)).performClick()
        rule.onNodeWithText(common(org.mochios.android.R.string.common_save)).performClick()
        assertEquals(listOf(NetworksViewModel.HOME_NEW), chosen)
    }

    @Test
    fun `a password is made for a named device and shown once, with the server and address to give the app`() {
        val account = NetworkAccount(id = "x1", type = "xmpp", hosted = true, identifier = "alice@a.example", domain = "a.example")
        rule.setContent { PasswordDialog(account = account, busy = false, onMake = { _, made -> made("s3cret") }, onDismiss = {}) }
        // Read-only fields: the server and the address to give the app.
        rule.onNode(hasText("a.example")).assertExists()
        rule.onNode(hasText("alice@a.example")).assertExists()
        rule.onNodeWithText(text(R.string.networks_password_make)).assertIsNotEnabled()
        rule.onNode(hasText(text(R.string.networks_device)) and hasSetTextAction()).performTextInput("Phone")
        rule.onNodeWithText(text(R.string.networks_password_make)).performClick()
        rule.onNode(hasText("s3cret")).assertExists()
        rule.onNodeWithText(text(R.string.networks_password_hint)).assertExists()
        rule.onNodeWithText(text(R.string.networks_password_make)).assertDoesNotExist()
    }

    @Test
    fun `a new Bluesky account's keys open on the recovery key, which is shown once made`() {
        val account = NetworkAccount(id = "b1", type = "bluesky", hosted = true)
        rule.setContent {
            KeysDialog(
                account = account,
                offer = true,
                busy = null,
                load = { listOf(AccountKey(kind = "signing", algorithm = "p256")) },
                onReplace = {},
                onRecovery = { made -> made("deadbeef") },
                onDismiss = {},
            )
        }
        rule.onNodeWithText(text(R.string.networks_recovery_title)).assertExists()
        rule.onNodeWithText(text(R.string.networks_recovery_make)).performClick()
        rule.onNode(hasText("deadbeef")).assertExists()
        rule.onNodeWithText("signing · p256").assertExists()
    }

    @Test
    fun `keys are replaced only once confirmed, and a fediverse account has no recovery key`() {
        val replaced = mutableListOf<Unit>()
        val account = NetworkAccount(id = "f1", type = "fediverse", hosted = true)
        rule.setContent {
            KeysDialog(account = account, offer = false, busy = null, load = { emptyList() }, onReplace = { replaced += Unit }, onRecovery = {}, onDismiss = {})
        }
        rule.onNodeWithText(text(R.string.networks_recovery)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.networks_replace_keys)).performClick()
        assertEquals(0, replaced.size)
        rule.onNodeWithText(text(R.string.networks_replace)).performClick()
        assertEquals(1, replaced.size)
    }
}
