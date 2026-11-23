// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.login

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mochios.android.auth.StepUpClient
import org.mochios.android.auth.StepUpResult

/**
 * A step-up backed out of tells the change that asked for it, so a control
 * that moved on the tap can move back; one that goes through does not.
 */
class StepUpControllerTest {
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
    private val errors = mutableListOf<Throwable>()
    private val stepUp = StepUpController(client, CoroutineScope(Dispatchers.Unconfined)) { errors.add(it) }

    @Test
    fun `a cancelled step-up tells the change it was for, and runs nothing`() {
        val cancelled = mutableListOf<String>()
        val ran = mutableListOf<String>()
        stepUp.request(onCancel = { cancelled.add("switch") }) { ran.add(it) }
        stepUp.cancel()
        assertEquals(listOf("switch"), cancelled)
        assertEquals(emptyList<String>(), ran)
        assertFalse(stepUp.visible.value)
        stepUp.cancel()
        assertEquals("told once", listOf("switch"), cancelled)
    }

    @Test
    fun `a verified step-up runs the change and does not cancel it`() {
        val cancelled = mutableListOf<String>()
        val ran = mutableListOf<String>()
        stepUp.request(onCancel = { cancelled.add("switch") }) { ran.add(it) }
        stepUp.onVerified("proof")
        stepUp.cancel()
        assertEquals(listOf("proof"), ran)
        assertEquals(emptyList<String>(), cancelled)
    }

    @Test
    fun `a request that replaces one still waiting cancels it`() {
        val cancelled = mutableListOf<String>()
        stepUp.request(onCancel = { cancelled.add("first") }) {}
        stepUp.request(onCancel = { cancelled.add("second") }) {}
        assertEquals(listOf("first"), cancelled)
    }

    @Test
    fun `a change that fails reaches onError, a cancelled coroutine does not`() {
        stepUp.request { throw IllegalStateException("refused") }
        stepUp.onVerified("proof")
        stepUp.request { throw CancellationException("left") }
        stepUp.onVerified("proof")
        assertEquals(listOf("refused"), errors.map { it.message })
    }
}
