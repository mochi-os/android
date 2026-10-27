// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.android.i18n.AppContext
import org.mochios.chat.model.ChatDetail
import org.mochios.chat.model.ChatMember
import org.mochios.chat.model.ChatMessage
import org.mochios.chat.model.ChatPreferences
import org.mochios.chat.model.ChatPresence
import org.mochios.chat.model.ChatQuote
import org.mochios.chat.model.EncryptionDevice
import org.mochios.chat.model.EncryptionFingerprint
import org.mochios.chat.model.EncryptionOverview
import org.mochios.chat.model.EncryptionPerson
import org.mochios.chat.model.EncryptionStatus
import org.mochios.chat.model.QuoteAuthor
import org.mochios.chat.model.ReceiptState
import org.mochios.chat.model.Verification
import org.mochios.chat.ui.chat.InvitationBar
import org.mochios.chat.ui.chat.MessageBubble
import org.mochios.chat.ui.chat.ThreadContent
import org.mochios.chat.ui.chat.ThreadState
import org.mochios.chat.ui.chat.TypingLine
import org.mochios.chat.ui.chat.standingLine
import org.mochios.chat.ui.encryption.EncryptionContent
import org.mochios.chat.ui.encryption.EncryptionUiState
import org.mochios.chat.ui.encryption.PersonDevices
import org.mochios.chat.ui.encryption.SetupDialog
import org.mochios.chat.ui.encryption.SetupState
import org.mochios.chat.ui.encryption.VerificationDialog
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.mochios.android.files.FileStore
import org.mochios.chat.api.ChatApi
import org.mochios.chat.repository.ChatRepository
import org.mochios.chat.ui.privacy.PrivacyDialog
import org.mochios.chat.ui.privacy.PrivacyFields
import org.mochios.chat.ui.privacy.PrivacyUiState
import org.mochios.chat.ui.privacy.PrivacyViewModel
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.atomic.AtomicReference
import org.mochios.chat.ui.settings.roleLabel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * What the Android chat shows of conversations on other networks and of the
 * signals Mochi's own conversations carry, and the controls behind them. No
 * display qualifiers: a dialog holding a text field never settles under them
 * in Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NetworksScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun begin() {
        AppContext.set(RuntimeEnvironment.getApplication())
    }

    private fun bubble(
        message: ChatMessage,
        own: Boolean = true,
        receipt: ReceiptState? = null,
        seen: List<String>? = null,
        onThread: (() -> Unit)? = null,
    ) {
        rule.setContent {
            MessageBubble(
                message = message,
                isOwn = own,
                receipt = receipt,
                seen = seen,
                onThread = onThread,
                isGroup = false,
                chatId = "c1",
                selectionMode = false,
                isSelected = false,
                isSearchMatch = false,
                searchQuery = "",
                replyToMessage = null,
                onStartSelect = {},
                onToggleSelect = {},
                onReply = {},
                onDelete = {},
                onReact = {},
                onForward = {},
                onEdit = {},
            )
        }
    }

    @Test
    fun `the user's own message carries a tick saying how far the others have it`() {
        bubble(ChatMessage(id = "m1", body = "Hello", member = "me", created = 10), receipt = ReceiptState.READ)
        rule.onNodeWithContentDescription("Read").assertIsDisplayed()
    }

    @Test
    fun `one sent is told apart from one delivered`() {
        bubble(ChatMessage(id = "m1", body = "Hello", member = "me", created = 10), receipt = ReceiptState.SENT)
        rule.onNodeWithContentDescription("Sent").assertIsDisplayed()
    }

    @Test
    fun `a message's thread is counted on it and opens from the count and the menu`() {
        var opened = 0
        bubble(ChatMessage(id = "m1", body = "Root", member = "you", replies = 2), own = false, onThread = { opened++ })
        rule.onNodeWithText("2 replies").performClick()
        assertEquals(1, opened)
        rule.onNodeWithText("Root").performTouchInput { longClick() }
        rule.onNodeWithText("Reply in thread").performClick()
        assertEquals(2, opened)
    }

    @Test
    fun `a conversation without threads counts none on its messages`() {
        bubble(ChatMessage(id = "m1", body = "Root", member = "you", replies = 2), own = false, onThread = null)
        rule.onNodeWithText("2 replies").assertDoesNotExist()
    }

    @Test
    fun `an encrypted message whose keys have not come says so`() {
        bubble(ChatMessage(id = "m1", body = "", member = "you", encrypted = 1), own = false)
        rule.onNodeWithText("Encrypted message").assertIsDisplayed()
    }

    @Test
    fun `a message quoting a post elsewhere shows the post`() {
        bubble(
            ChatMessage(
                id = "m1", body = "Look", member = "you",
                quote = ChatQuote(network = "bluesky", url = "https://bsky.app/x", text = "Quoted words", author = QuoteAuthor(name = "Carol")),
            ),
            own = false,
        )
        rule.onNodeWithText("Quoted words").assertIsDisplayed()
        rule.onNodeWithText("Carol").assertIsDisplayed()
        rule.onNodeWithText("Bluesky").assertIsDisplayed()
    }

    @Test
    fun `the user's newest message in a group says who has read it`() {
        bubble(ChatMessage(id = "m1", body = "Hello", member = "me"), seen = listOf("Adam", "Zoe"))
        rule.onNodeWithText("Seen by", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Adam", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Zoe", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the typing line names one typist, or several`() {
        rule.setContent {
            Column {
                TypingLine(listOf("Ann"))
                TypingLine(listOf("Ann", "Bob"))
            }
        }
        rule.onNodeWithText("Ann is typing...").assertIsDisplayed()
        rule.onNodeWithText("are typing...", substring = true).assertIsDisplayed()
    }

    @Test
    fun `an invitation is accepted or declined in place of the composer`() {
        var accepted = 0
        var declined = 0
        rule.setContent { InvitationBar(answering = false, onAccept = { accepted++ }, onDecline = { declined++ }) }
        rule.onNodeWithText("You are invited to this conversation").assertIsDisplayed()
        rule.onNodeWithText("Accept").performClick()
        rule.onNodeWithText("Decline").performClick()
        assertEquals(1, accepted)
        assertEquals(1, declined)
    }

    @Test
    fun `while an invitation is answered neither choice can be made again`() {
        rule.setContent { InvitationBar(answering = true, onAccept = {}, onDecline = {}) }
        rule.onNodeWithText("Decline").assertIsNotEnabled()
    }

    private val direct = ChatDetail(members = listOf(ChatMember("me"), ChatMember("you")))

    @Test
    fun `a one-to-one conversation says the other person is online, with their status`() {
        rule.setContent { Text(standingLine(direct, ChatPresence(status = "away", message = "Lunch")).orEmpty()) }
        rule.onNodeWithText("Away · Lunch").assertIsDisplayed()
    }

    @Test
    fun `someone offline on Mochi is shown as last seen`() {
        rule.setContent { Text(standingLine(direct, ChatPresence(status = "offline", seen = 1_700_000_000)).orEmpty()) }
        rule.onNodeWithText("Last seen", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a conversation on another network names the account it is had through where its network says nothing`() {
        val xmpp = direct.copy(account = "x1", network = "xmpp", address = "me@xmpp.example")
        rule.setContent { Text(standingLine(xmpp, ChatPresence(status = "offline", seen = 5)).orEmpty()) }
        rule.onNodeWithText("As me@xmpp.example").assertIsDisplayed()
    }

    @Test
    fun `a thread shows its message and replies, pages back, and takes a reply`() {
        var earlier = 0
        var sent = 0
        var draft = ""
        rule.setContent {
            Column {
                ThreadContent(
                    thread = ThreadState(
                        root = ChatMessage(id = "r", name = "Ann", body = "Root words"),
                        replies = listOf(ChatMessage(id = "x", name = "Bob", body = "Reply words")),
                        more = true,
                        isLoading = false,
                        draft = draft,
                    ),
                    chatId = "c1",
                    canWrite = true,
                    onDraft = { draft = it },
                    onSend = { sent++ },
                    onEarlier = { earlier++ },
                )
            }
        }
        rule.onNodeWithText("Root words").assertIsDisplayed()
        rule.onNodeWithText("Reply words").assertIsDisplayed()
        rule.onNodeWithText("Show earlier replies").performClick()
        assertEquals(1, earlier)
        rule.onNode(hasSetTextAction()).performTextInput("Hi")
        assertEquals("Hi", draft)
    }

    @Test
    fun `a conversation the user cannot write to shows its thread without a reply box`() {
        rule.setContent {
            Column {
                ThreadContent(
                    thread = ThreadState(root = ChatMessage(id = "r", body = "Root words"), isLoading = false),
                    chatId = "c1",
                    canWrite = false,
                    onDraft = {},
                    onSend = {},
                    onEarlier = {},
                )
            }
        }
        rule.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test
    fun `the privacy switches change what the user shares`() {
        var value: ChatPreferences? = null
        rule.setContent {
            PrivacyFields(PrivacyUiState(loaded = ChatPreferences(), value = ChatPreferences()), onChange = { value = it })
        }
        rule.onNodeWithText("Show when I'm typing").performClick()
        assertEquals(ChatPreferences(typing = false), value)
        rule.onNodeWithText("Anyone").performClick()
        assertEquals(ChatPreferences(policy = "anyone"), value)
    }

    /** Idles the main thread until [done], a server's answer landing on it. */
    private fun idleUntil(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            rule.waitForIdle()
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    @Test
    fun `the privacy dialog reads the settings again each time it opens, dropping an edit left unsaved`() {
        val policy = AtomicReference("friends")
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path.orEmpty().substringBefore("?") == "/chat/-/preferences/get") {
                    MockResponse().setBody("""{"data": {"policy": "${policy.get()}", "presence": true, "receipts": true, "typing": true}}""")
                } else {
                    MockResponse().setResponseCode(404)
                }
        }
        server.start()
        try {
            val context = RuntimeEnvironment.getApplication()
            val api = Retrofit.Builder()
                .baseUrl(server.url("/chat/"))
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ChatApi::class.java)
            val model = PrivacyViewModel(ChatRepository(api, FileStore(context), context))
            var open by mutableStateOf(true)
            rule.setContent {
                if (open) PrivacyDialog(onDismiss = { open = false }, viewModel = model)
            }
            idleUntil { model.uiState.value.loaded != null }
            rule.onNodeWithText("Show when I'm typing").performClick()
            assertEquals(false, model.uiState.value.value.typing)
            rule.onNodeWithText("Cancel").performClick()
            rule.waitForIdle()
            // Changed elsewhere while the dialog was closed.
            policy.set("anyone")
            rule.runOnIdle { open = true }
            idleUntil { model.uiState.value.loaded?.policy == "anyone" }
            assertEquals(model.uiState.value.loaded, model.uiState.value.value)
            assertEquals(ChatPreferences(policy = "anyone"), model.uiState.value.value)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `a privacy dialog reopened while the settings cannot be read offers nothing to save over them`() {
        val down = java.util.concurrent.atomic.AtomicBoolean(false)
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path.orEmpty().substringBefore("?") != "/chat/-/preferences/get" -> MockResponse().setResponseCode(404)
                down.get() -> MockResponse().setResponseCode(500).setBody("""{"error": "down"}""")
                else -> MockResponse().setBody("""{"data": {"policy": "friends", "presence": true, "receipts": true, "typing": true}}""")
            }
        }
        server.start()
        try {
            val context = RuntimeEnvironment.getApplication()
            val api = Retrofit.Builder()
                .baseUrl(server.url("/chat/"))
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ChatApi::class.java)
            val model = PrivacyViewModel(ChatRepository(api, FileStore(context), context))
            var open by mutableStateOf(true)
            rule.setContent {
                if (open) PrivacyDialog(onDismiss = { open = false }, viewModel = model)
            }
            idleUntil { model.uiState.value.loaded != null }
            rule.onNodeWithText("Show when I'm typing").performClick()
            rule.onNodeWithText("Cancel").performClick()
            rule.waitForIdle()
            down.set(true)
            rule.runOnIdle { open = true }
            idleUntil { model.uiState.value.failure != null }
            assertEquals(null, model.uiState.value.loaded)
            assertEquals(false, model.uiState.value.changed)
            rule.onNodeWithText("Show when I'm typing").assertDoesNotExist()
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `privacy settings that could not be read show why, with no controls to save over them`() {
        rule.setContent {
            PrivacyFields(PrivacyUiState(failure = MochiError.Unknown("Refused")), onChange = {})
        }
        rule.onNodeWithText("Refused").assertIsDisplayed()
        rule.onNodeWithText("Show when I'm online").assertDoesNotExist()
    }

    @Test
    fun `emoji to compare are confirmed or refused`() {
        val acts = mutableListOf<String>()
        rule.setContent {
            VerificationDialog(
                Verification(flow = "f", person = "@carol:m.example", name = "Carol", room = "!r", phase = "compare", emoji = listOf("🐶", "🔑")),
                acting = false,
                onAct = { acts += it },
                onClose = { acts += "close" },
            )
        }
        rule.onNodeWithText("Verify Carol").assertIsDisplayed()
        rule.onNodeWithText("🐶").assertIsDisplayed()
        rule.onNodeWithText("They match").performClick()
        rule.onNodeWithText("They don't match").performClick()
        assertEquals(listOf("confirm", "mismatch"), acts)
    }

    @Test
    fun `a request from another of the user's devices is accepted or declined`() {
        val acts = mutableListOf<String>()
        rule.setContent {
            VerificationDialog(
                Verification(flow = "f", person = "@me:m.example", phase = "requested"),
                acting = false,
                onAct = { acts += it },
                onClose = { acts += "close" },
            )
        }
        rule.onNodeWithText("Verify your device").assertIsDisplayed()
        rule.onNodeWithText("Another of your devices wants to verify this one.").assertIsDisplayed()
        rule.onNodeWithText("Accept").performClick()
        rule.onNodeWithText("Decline").performClick()
        assertEquals(listOf("accept", "close"), acts)
    }

    @Test
    fun `once both sides are ready, the emoji are asked for`() {
        val acts = mutableListOf<String>()
        rule.setContent {
            VerificationDialog(Verification(flow = "f", room = "!r", phase = "ready", started = true), acting = false, onAct = { acts += it }, onClose = {})
        }
        rule.onNodeWithText("Compare emoji").performClick()
        assertEquals(listOf("start"), acts)
    }

    private fun encryption(
        state: EncryptionUiState,
        toggled: MutableList<String> = mutableListOf(),
        trusted: MutableList<String> = mutableListOf(),
    ) {
        rule.setContent {
            Column {
                EncryptionContent(
                    state = state,
                    onRetry = {},
                    onToggle = { toggled += it },
                    onTrust = { person, device, trust -> trusted += "$person/$device/$trust" },
                    onVerify = {},
                    onSetup = {},
                    onRecover = {},
                )
            }
        }
    }

    @Test
    fun `a Matrix conversation shows the device, whether it is verified, and recovery to set up`() {
        encryption(
            EncryptionUiState(
                overview = EncryptionOverview(
                    status = EncryptionStatus(network = "matrix", address = "@me:m.example", fingerprint = "abcdefghijkl", verified = true, signing = true),
                    people = listOf(EncryptionPerson("@carol:m.example", "Carol")),
                ),
            ),
        )
        rule.onNodeWithText("abcdefgh ijkl").assertIsDisplayed()
        rule.onNodeWithText("Verified").assertIsDisplayed()
        rule.onNodeWithText("Set up recovery").assertIsDisplayed()
        rule.onNodeWithText("Enter recovery key").assertIsDisplayed()
        rule.onNodeWithText("Carol").assertIsDisplayed()
    }

    @Test
    fun `an XMPP conversation shows the device in both versions of OMEMO, and no recovery`() {
        val toggled = mutableListOf<String>()
        encryption(
            EncryptionUiState(
                overview = EncryptionOverview(
                    status = EncryptionStatus(
                        network = "xmpp", address = "me@xmpp.example",
                        fingerprints = listOf(EncryptionFingerprint("eu.siacs.conversations.axolotl", "11112222"), EncryptionFingerprint("urn:xmpp:omemo:2", "33334444")),
                    ),
                ),
            ),
            toggled = toggled,
        )
        rule.onNodeWithText("This device (OMEMO)").assertIsDisplayed()
        rule.onNodeWithText("This device (OMEMO 2)").assertIsDisplayed()
        rule.onNodeWithText("Set up recovery").assertDoesNotExist()
        rule.onNodeWithText("Compare emoji").assertDoesNotExist()
        rule.onNodeWithText("Your other devices").performClick()
        assertEquals(listOf("xmpp:me@xmpp.example"), toggled)
    }

    @Test
    fun `a person's device takes the user's word on it`() {
        val trusted = mutableListOf<String>()
        encryption(
            EncryptionUiState(
                overview = EncryptionOverview(
                    status = EncryptionStatus(network = "xmpp", address = "me@xmpp.example"),
                    people = listOf(EncryptionPerson("xmpp:dave@xmpp.example", "Dave")),
                ),
                open = setOf("xmpp:dave@xmpp.example"),
                devices = mapOf("xmpp:dave@xmpp.example" to PersonDevices(isLoading = false, devices = listOf(EncryptionDevice(id = "7", fingerprint = "aaaabbbb", trust = "undecided"), EncryptionDevice(id = "D", name = "This phone", own = true)))),
            ),
            trusted = trusted,
        )
        rule.onNodeWithText("Not verified").assertIsDisplayed()
        // The device the server holds is shown as this one, not among theirs.
        rule.onNodeWithText("This phone").assertDoesNotExist()
        rule.onNodeWithContentDescription("Device options").performClick()
        rule.onNodeWithText("The fingerprint matches").performClick()
        assertEquals(listOf("xmpp:dave@xmpp.example/7/verified"), trusted)
    }

    @Test
    fun `setting up recovery asks for the password where the server wants it, then shows the key once`() {
        var password = ""
        rule.setContent { SetupDialog(SetupState(asked = true), onPassword = { password = it }, onSetup = {}, onClose = {}) }
        rule.onNodeWithText("Your Matrix server asks for your account's password.").assertIsDisplayed()
        rule.onNodeWithText("Password").performTextInput("secret")
        assertEquals("secret", password)
    }

    @Test
    fun `the recovery key is shown with its warning`() {
        rule.setContent { SetupDialog(SetupState(recovery = "EsTc abcd"), onPassword = {}, onSetup = {}, onClose = {}) }
        rule.onNodeWithText("Your recovery key").assertIsDisplayed()
        rule.onNodeWithText("EsTc abcd").assertIsDisplayed()
        rule.onNodeWithText("Done").assertIsDisplayed()
    }

    @Test
    fun `a member's role in a room elsewhere is named in the user's words`() {
        rule.setContent {
            Column {
                Text(roleLabel("admin").orEmpty())
                Text(roleLabel("visitor").orEmpty())
                Text("none:" + roleLabel("").orEmpty())
            }
        }
        rule.onNodeWithText("Administrator").assertIsDisplayed()
        rule.onNodeWithText("Visitor").assertIsDisplayed()
        rule.onNodeWithText("none:").assertIsDisplayed()
    }
}
