// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.StepUpClient
import org.mochios.android.auth.StepUpResult
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StepUpDialogTest {

    @get:Rule
    val rule = createComposeRule()

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
}
