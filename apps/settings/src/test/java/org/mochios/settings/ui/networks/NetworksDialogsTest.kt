// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.networks

import android.app.Activity
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.android.i18n.AppContext
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
import org.robolectric.shadows.ShadowDialog

/**
 * The Networks screen's dialogs with fields. Plain @Config: a MochiAlertDialog
 * with text fields never idles under display qualifiers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NetworksDialogsTest {

    @get:Rule
    val rule = createComposeRule()

    // A failure's words come from the app's catalogue where the server gave none.
    @Before
    fun begin() {
        AppContext.set(RuntimeEnvironment.getApplication())
    }

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
    fun `the link dialog opens on the provider an app sent the user here for`() {
        val providers = listOf(
            NetworkProvider(type = "lemmy", link = NetworkLink(flow = "form", fields = listOf(ProviderField(name = "server", label = "Server URL", required = true)))),
            NetworkProvider(type = "mastodon", link = NetworkLink(flow = "oauth", fields = listOf(ProviderField(name = "server", label = "Server URL", required = true)))),
        )
        val linked = mutableListOf<String>()
        rule.setContent {
            LinkDialog(providers = providers, busy = false, onLink = { type, _, _ -> linked += type }, onDismiss = {}, initial = "mastodon")
        }
        rule.onNodeWithText("Mastodon").assertExists()
        rule.onNodeWithText("Server URL").performTextInput("mastodon.social")
        rule.onNodeWithText(text(org.mochios.android.R.string.common_continue)).performClick()
        assertEquals(listOf("mastodon"), linked)
    }

    @Test
    fun `a name under a handle is offered once the server says it is free`() {
        val added = mutableListOf<String>()
        rule.setContent {
            AddNameDialog(
                parent = alice,
                busy = false,
                check = { name, domain -> Result.success(Availability(available = name == "alice_news", name = name, domain = domain)) },
                onAdd = { added += it },
                onDismiss = {},
            )
        }
        rule.onNode(hasSetTextAction()).performTextInput("News!")
        rule.waitUntil(5000) { rule.onAllNodes(androidx.compose.ui.test.hasText(text(R.string.networks_available))).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(text(org.mochios.android.R.string.common_add)).performClick()
        assertEquals(listOf("alice_news"), added)
    }

    @Test
    fun `a name whose check fails says why, under a handle and in the chooser, and cannot be taken`() {
        rule.setContent {
            AddNameDialog(
                parent = alice,
                busy = false,
                check = { _, _ -> Result.failure(MochiError.Unknown("Checks are paused")) },
                onAdd = {},
                onDismiss = {},
            )
        }
        rule.onNode(hasSetTextAction()).performTextInput("news")
        // THE POINT: a check that failed is told apart from one still coming,
        // in the server's words, and the name is not offered.
        rule.waitUntil(5000) { rule.onAllNodes(hasText("Checks are paused")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(text(R.string.networks_checking)).assertDoesNotExist()
        rule.onNodeWithText(text(org.mochios.android.R.string.common_add)).assertIsNotEnabled()
    }

    @Test
    fun `a handle whose check fails without a reason says the check failed, and cannot be reserved`() {
        rule.setContent {
            HandleChooser(
                domains = listOf("a.example"),
                suggestion = "alice",
                title = "Choose a handle",
                reserving = false,
                check = { _, _ -> Result.failure(MochiError.Unknown()) },
                onReserve = { _, _, _ -> },
            )
        }
        rule.waitUntil(5000) { rule.onAllNodes(hasText(text(R.string.networks_check_failed))).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(text(R.string.networks_reserve)).assertIsNotEnabled()
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
    fun `a hosted Bluesky account with no home feed chooses one with its password, among the public feeds or a new one`() {
        val made = mutableListOf<Pair<String, String?>>()
        val feeds = listOf(
            NetworkFeed(id = "t", name = "Travel", privacy = "public"),
            NetworkFeed(id = "n", name = "notes", privacy = "public"),
            NetworkFeed(id = "d", name = "Diary", privacy = "private"),
        )
        var account by mutableStateOf(NetworkAccount(id = "b1", type = "bluesky", hosted = true, identifier = "alice.a.example", domain = "a.example"))
        rule.setContent {
            PasswordDialog(state = PasswordDialogState(account), feeds = feeds, busy = false, onMake = { device, home -> made += device to home }, onDismiss = {})
        }
        // THE POINT: the home feed is asked for; the first public feed by name is offered first.
        rule.onNodeWithText("notes").performScrollTo().performClick()
        rule.onNodeWithText("Diary").assertDoesNotExist()
        rule.onNodeWithText("Travel").performClick()
        rule.onNode(hasText(text(R.string.networks_device)) and hasSetTextAction()).performScrollTo().performTextInput("Phone")
        rule.onNodeWithText(text(R.string.networks_password_make)).performClick()
        assertEquals(listOf("Phone" to "t"), made)

        // An account that has a home feed is not asked.
        account = account.copy(home = "n")
        rule.waitForIdle()
        rule.onNodeWithText(text(R.string.networks_home)).assertDoesNotExist()
    }

    @Test
    fun `a password is made for a named device and shown once, with the server and address to give the app`() {
        val account = NetworkAccount(id = "x1", type = "xmpp", hosted = true, identifier = "alice@a.example", domain = "a.example")
        var state by mutableStateOf(PasswordDialogState(account))
        rule.setContent {
            PasswordDialog(state = state, feeds = emptyList(), busy = false, onMake = { _, _ -> state = state.copy(password = "s3cret") }, onDismiss = {})
        }
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
        var state by mutableStateOf(KeysDialogState(account, offer = true, keys = listOf(AccountKey(kind = "signing", algorithm = "p256"))))
        rule.setContent {
            KeysDialog(
                state = state,
                busy = emptySet(),
                onReplace = {},
                onRecovery = { state = state.copy(secret = "deadbeef", offer = false) },
                onRetry = {},
                onDismiss = {},
            )
        }
        rule.onNodeWithText(text(R.string.networks_recovery_title)).assertExists()
        rule.onNodeWithText(text(R.string.networks_recovery_make)).performClick()
        rule.onNode(hasText("deadbeef")).assertExists()
        rule.onNodeWithText("${text(R.string.networks_key_signing)} · p256").assertExists()
    }

    @Test
    fun `each key's kind is named in the user's words, never as stored`() {
        val keys = listOf(
            AccountKey(kind = "signing", algorithm = "p256"),
            AccountKey(kind = "rotation", algorithm = "p256"),
            AccountKey(kind = "recovery", algorithm = "p256"),
            AccountKey(kind = "future", algorithm = "ed25519"),
        )
        rule.setContent {
            KeysDialog(
                state = KeysDialogState(NetworkAccount(id = "b1", type = "bluesky", hosted = true), keys = keys),
                busy = emptySet(),
                onReplace = {},
                onRecovery = {},
                onRetry = {},
                onDismiss = {},
            )
        }
        rule.onNodeWithText("${text(R.string.networks_key_signing)} · p256").assertExists()
        rule.onNodeWithText("${text(R.string.networks_key_rotation)} · p256").assertExists()
        rule.onNodeWithText("${text(R.string.networks_key_recovery)} · p256").assertExists()
        rule.onNodeWithText("${text(R.string.networks_key_other)} · ed25519").assertExists()
        for (raw in listOf("signing", "rotation", "recovery", "future")) {
            rule.onNodeWithText("$raw · ", substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun `keys that could not be read say why instead of loading for ever, and are read again on Retry`() {
        var retried = 0
        rule.setContent {
            KeysDialog(
                state = KeysDialogState(NetworkAccount(id = "f1", type = "fediverse", hosted = true), failure = "No keys here"),
                busy = emptySet(),
                onReplace = {},
                onRecovery = {},
                onRetry = { retried++ },
                onDismiss = {},
            )
        }
        rule.onNodeWithText("No keys here").assertExists()
        rule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertDoesNotExist()
        rule.onNodeWithText(text(org.mochios.android.R.string.common_retry)).performClick()
        assertEquals(1, retried)
    }

    /** The dialog last shown, with Back pressed on it or a tap outside it. */
    private fun dialog() = ShadowDialog.getLatestDialog() as ComponentDialog

    private fun back() {
        rule.runOnUiThread { dialog().onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    /** A tap far beside the dialog's content: pressed and lifted there. */
    private fun outside() {
        rule.runOnUiThread {
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(0, 0, action, -50f, -50f, 0)
                dialog().onTouchEvent(event)
                event.recycle()
            }
        }
        rule.waitForIdle()
    }

    private fun secure(host: Context?): Boolean =
        (host as Activity).window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    @Test
    fun `a recovery key on screen is closed only by Close, kept out of captures, and copied only as a secret`() {
        var host: Context? = null
        var dismissed = 0
        val account = NetworkAccount(id = "b1", type = "bluesky", hosted = true)
        var state by mutableStateOf(KeysDialogState(account, keys = emptyList()))
        rule.setContent {
            host = LocalContext.current
            KeysDialog(state = state, busy = emptySet(), onReplace = {}, onRecovery = {}, onRetry = {}, onDismiss = { dismissed++ })
        }
        // With no key on screen, Back and a tap outside close it as any dialog.
        back()
        outside()
        assertEquals(2, dismissed)
        assertFalse(secure(host))

        state = state.copy(secret = "deadbeef")
        rule.waitForIdle()
        // THE POINT: while the key shows, neither loses it.
        back()
        outside()
        assertEquals(2, dismissed)
        rule.onNodeWithText("deadbeef").assertExists()
        // Kept out of screenshots, recordings and Recents.
        assertTrue(secure(host))
        // Plain text, which cannot be selected and copied as an ordinary clip.
        rule.onNodeWithText("deadbeef").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.EditableText))
        rule.onNodeWithContentDescription(text(org.mochios.android.R.string.common_copy)).performClick()
        rule.waitForIdle()
        val clipboard = RuntimeEnvironment.getApplication().getSystemService(ClipboardManager::class.java)
        assertEquals("deadbeef", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        assertEquals(true, clipboard.primaryClipDescription?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
        rule.onNodeWithText(text(org.mochios.android.R.string.common_close)).performClick()
        assertEquals(3, dismissed)
    }

    @Test
    fun `a minted password on screen is closed only by Close, kept out of captures, and copied only as a secret`() {
        var host: Context? = null
        var dismissed = 0
        val account = NetworkAccount(id = "x1", type = "xmpp", hosted = true, identifier = "alice@a.example", domain = "a.example")
        var state by mutableStateOf(PasswordDialogState(account))
        rule.setContent {
            host = LocalContext.current
            PasswordDialog(state = state, feeds = emptyList(), busy = false, onMake = { _, _ -> }, onDismiss = { dismissed++ })
        }
        back()
        outside()
        assertEquals(2, dismissed)
        assertFalse(secure(host))

        state = state.copy(password = "s3cret")
        rule.waitForIdle()
        // THE POINT: while the password shows, neither loses it.
        back()
        outside()
        assertEquals(2, dismissed)
        rule.onNodeWithText("s3cret").assertExists()
        assertTrue(secure(host))
        rule.onNodeWithText("s3cret").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.EditableText))
        // The password's own copy button is the last: the server's and the address's come first.
        rule.onAllNodesWithContentDescription(text(org.mochios.android.R.string.common_copy)).onLast().performClick()
        rule.waitForIdle()
        val clipboard = RuntimeEnvironment.getApplication().getSystemService(ClipboardManager::class.java)
        assertEquals("s3cret", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        assertEquals(true, clipboard.primaryClipDescription?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
        rule.onNodeWithText(text(org.mochios.android.R.string.common_close)).performClick()
        assertEquals(3, dismissed)
    }

    @Test
    fun `keys are replaced only once confirmed, and a fediverse account has no recovery key`() {
        val replaced = mutableListOf<Unit>()
        val account = NetworkAccount(id = "f1", type = "fediverse", hosted = true)
        rule.setContent {
            KeysDialog(
                state = KeysDialogState(account, keys = emptyList()),
                busy = emptySet(),
                onReplace = { replaced += Unit },
                onRecovery = {},
                onRetry = {},
                onDismiss = {},
            )
        }
        rule.onNodeWithText(text(R.string.networks_recovery)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.networks_replace_keys)).performClick()
        assertEquals(0, replaced.size)
        rule.onNodeWithText(text(R.string.networks_replace)).performClick()
        assertEquals(1, replaced.size)
    }
}
