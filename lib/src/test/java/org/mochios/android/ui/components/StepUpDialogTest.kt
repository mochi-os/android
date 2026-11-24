// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.ApiError
import org.mochios.android.api.ApiException
import org.mochios.android.auth.StepUpClient
import org.mochios.android.auth.StepUpResult
import org.mochios.android.i18n.AppContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * A step-up whose only offered factor is OAuth, and whose provider never
 * answers: the user opened the browser and walked away from it.
 */
private class AbandonedOAuth : StepUpClient {
    var polling = false
    var cancelled = false

    override suspend fun methods(): List<String> = emptyList()
    override suspend fun send() = Unit
    override suspend fun verifyEmail(code: String) = StepUpResult()
    override suspend fun verifyTotp(code: String) = StepUpResult()
    override suspend fun passkey() = StepUpResult()
    override suspend fun oauthProviders(): List<String> = listOf("google")
    override suspend fun oauthBegin(provider: String): String = "https://example.com/oauth"

    override suspend fun oauthPoll(): StepUpResult {
        polling = true
        try {
            awaitCancellation()
        } finally {
            cancelled = true
        }
    }
}

/** The server's labelled refusal, as an app's action answers one. */
private fun refusal(message: String) = ApiException(400, ApiError(message = message))

/**
 * A step-up offering [factors] (and Google when [linked]), whose calls fail
 * with what the test puts in [sends], [code], [passkeyFailure] or [begin].
 */
private class ScriptedStepUp(
    private val factors: List<String>,
    private val linked: Boolean = false,
) : StepUpClient {
    /** One outcome per send, in order: null succeeds. */
    val sends = ArrayDeque<Exception?>()
    var code: Exception? = null
    var passkeyFailure: Exception? = null
    var begin: Exception? = null
    var start: Exception? = null
    val codes = mutableListOf<String>()

    override suspend fun methods(): List<String> = start?.let { throw it } ?: factors
    override suspend fun send() {
        sends.removeFirstOrNull()?.let { throw it }
    }
    override suspend fun verifyEmail(code: String): StepUpResult {
        codes += code
        this.code?.let { throw it }
        return StepUpResult(token = "proof")
    }
    override suspend fun verifyTotp(code: String): StepUpResult {
        codes += code
        this.code?.let { throw it }
        return StepUpResult(token = "proof")
    }
    override suspend fun passkey(): StepUpResult = throw passkeyFailure ?: IllegalStateException()
    override suspend fun oauthProviders(): List<String> = if (linked) listOf("google") else emptyList()
    override suspend fun oauthBegin(provider: String): String = throw begin ?: IllegalStateException()
    override suspend fun oauthPoll() = StepUpResult()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StepUpDialogTest {

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun begin() {
        AppContext.set(RuntimeEnvironment.getApplication())
    }

    private fun show(client: StepUpClient, onVerified: (String) -> Unit = {}) {
        rule.setContent {
            StepUpDialog(client = client, onDismiss = {}, onVerified = onVerified)
        }
        rule.waitForIdle()
    }

    @Test
    fun `cancel leaves the dialog while the OAuth browser wait is running, and stops the wait`() {
        val client = AbandonedOAuth()
        var dismissed = 0
        rule.setContent {
            StepUpDialog(client = client, onDismiss = { dismissed++ }, onVerified = {})
        }
        rule.onNodeWithText("Continue with Google").performClick()
        rule.mainClock.advanceTimeBy(1_500)
        rule.waitForIdle()
        assertTrue("the poll should be waiting on the provider", client.polling)
        assertFalse(client.cancelled)

        rule.onNodeWithText("Cancel").assertIsEnabled().performClick()
        rule.waitForIdle()

        assertEquals(1, dismissed)
        assertTrue("leaving should cancel the poll", client.cancelled)
    }

    @Test
    fun `a code sent on opening shows the code field`() {
        show(ScriptedStepUp(listOf("email")))
        rule.onNodeWithText("Email code").assertExists()
        rule.onNodeWithText("Send email").assertDoesNotExist()
    }

    @Test
    fun `a failed send on opening says why and leaves the Send button`() {
        val client = ScriptedStepUp(listOf("email")).apply { sends += refusal("Too many codes sent") }
        show(client)
        rule.onNodeWithText("Too many codes sent").assertExists()
        rule.onNodeWithText("Send email").assertExists()
        rule.onNodeWithText("Email code").assertDoesNotExist()
    }

    @Test
    fun `a failed tap on Send says why`() {
        val client = ScriptedStepUp(listOf("email"), linked = true).apply { sends += IOException() }
        show(client)
        rule.onNodeWithText("Send email").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Unable to connect. Check your internet connection and try again.").assertExists()
        rule.onNodeWithText("Email code").assertDoesNotExist()
    }

    @Test
    fun `a failed resend says why`() {
        val client = ScriptedStepUp(listOf("email")).apply {
            sends += null
            sends += refusal("Too many codes sent")
        }
        show(client)
        rule.onNodeWithText("Resend email").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Too many codes sent").assertExists()
    }

    @Test
    fun `a refused code shows the server's reason`() {
        val client = ScriptedStepUp(listOf("totp")).apply { code = refusal("Code already used") }
        show(client)
        rule.onNode(hasSetTextAction()).performTextInput("123456")
        rule.onNodeWithText("Verify").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Code already used").assertExists()
        rule.onNodeWithText("That code is incorrect or has expired.").assertDoesNotExist()
    }

    @Test
    fun `a code that cannot reach the server says so, not that it is wrong`() {
        val client = ScriptedStepUp(listOf("totp")).apply { code = IOException() }
        show(client)
        rule.onNode(hasSetTextAction()).performTextInput("123456")
        rule.onNodeWithText("Verify").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Unable to connect. Check your internet connection and try again.").assertExists()
    }

    @Test
    fun `a refused passkey shows the server's reason`() {
        val client = ScriptedStepUp(listOf("passkey")).apply { passkeyFailure = refusal("Passkey not recognised") }
        show(client)
        rule.onNodeWithText("Use your passkey").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Passkey not recognised").assertExists()
    }

    @Test
    fun `a passkey failure with no reason keeps the fixed sentence`() {
        val client = ScriptedStepUp(listOf("passkey")).apply { passkeyFailure = IllegalStateException() }
        show(client)
        rule.onNodeWithText("Use your passkey").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Passkey verification failed. Please try again.").assertExists()
    }

    @Test
    fun `a refused OAuth start shows the server's reason`() {
        val client = ScriptedStepUp(emptyList(), linked = true).apply { begin = refusal("Provider unavailable") }
        show(client)
        rule.onNodeWithText("Continue with Google").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Provider unavailable").assertExists()
    }

    @Test
    fun `a refused start shows the server's reason`() {
        val client = ScriptedStepUp(listOf("email")).apply { start = refusal("Session expired") }
        show(client)
        rule.onNodeWithText("Session expired").assertExists()
    }

    @Test
    fun `an authenticator code typed in another script's digits is sent as ASCII`() {
        val client = ScriptedStepUp(listOf("totp"))
        var proof = ""
        show(client) { token -> proof = token }
        rule.onNode(hasSetTextAction()).performTextInput("١٢٣٤٥٦")
        rule.onNodeWithText("Verify").performClick()
        rule.waitForIdle()
        assertEquals(listOf("123456"), client.codes)
        assertEquals("proof", proof)
    }

    /** The input type the keyboard is asked for when the emailed code's field is tapped. */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun emailCodeInput(): Int {
        var inputType = 0
        rule.setContent {
            InterceptPlatformTextInput(
                interceptor = { request, next ->
                    val info = EditorInfo()
                    request.createInputConnection(info)
                    inputType = info.inputType
                    next.startInputMethod(request)
                },
            ) {
                StepUpDialog(client = ScriptedStepUp(listOf("email")), onDismiss = {}, onVerified = {})
            }
        }
        rule.waitForIdle()
        rule.onNode(hasSetTextAction()).performClick()
        rule.waitForIdle()
        return inputType
    }

    @Test
    fun `the emailed code is typed on a text keyboard, since it holds letters`() {
        assertEquals(InputType.TYPE_CLASS_TEXT, emailCodeInput() and InputType.TYPE_MASK_CLASS)
    }

    /** A keyboard correcting a mixed-case code as a word would change what was typed. */
    @Test
    fun `the emailed code is not corrected as it is typed`() {
        assertEquals(0, emailCodeInput() and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
    }
}
