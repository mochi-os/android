// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.networks

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.android.auth.StepUpClient
import org.mochios.android.auth.StepUpResult
import org.mochios.settings.ui.login.StepUpController

/**
 * Linking a Mastodon or Bluesky account, which becomes a way to sign in,
 * takes the step-up; another network's account links at once.
 */
class LinkGatedTest {
    /** The dialog's client, never reached: these tests settle the step-up themselves. */
    private val client = object : StepUpClient {
        override suspend fun methods(): List<String> = emptyList()
        override suspend fun send() = Unit
        override suspend fun verifyEmail(code: String) = StepUpResult()
        override suspend fun verifyTotp(code: String) = StepUpResult()
        override suspend fun passkey() = StepUpResult()
        override suspend fun oauthProviders(): List<String> = emptyList()
        override suspend fun oauthBegin(provider: String) = ""
        override suspend fun oauthPoll() = StepUpResult()
    }
    private val stepUp = StepUpController(client, CoroutineScope(Dispatchers.Unconfined)) {}

    @Test
    fun `a Mastodon or Bluesky account links only after a step-up, with its proof`() {
        for (type in listOf("mastodon", "bluesky")) {
            val sent = mutableListOf<String?>()
            linkGated(type, stepUp) { sent.add(it) }
            assertTrue(stepUp.visible.value)
            assertEquals(emptyList<String?>(), sent)
            stepUp.onVerified("proof")
            assertEquals(listOf<String?>("proof"), sent)
        }
    }

    @Test
    fun `another network's account links at once with no proof`() {
        for (type in listOf("lemmy", "xmpp", "matrix")) {
            val sent = mutableListOf<String?>()
            linkGated(type, stepUp) { sent.add(it) }
            assertFalse(stepUp.visible.value)
            assertEquals(listOf<String?>(null), sent)
        }
    }
}
