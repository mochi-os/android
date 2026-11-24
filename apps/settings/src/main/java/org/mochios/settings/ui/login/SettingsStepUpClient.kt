// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.login

import org.mochios.android.api.MochiError
import org.mochios.android.api.unwrapRaw
import org.mochios.android.auth.OAuthPkce
import org.mochios.android.auth.OAuthVerifyReturn
import org.mochios.android.auth.PasskeyManager
import org.mochios.android.auth.StepUpClient
import org.mochios.android.auth.StepUpResult
import org.mochios.settings.api.AccountApi
import org.mochios.settings.api.MethodsResponse
import org.mochios.settings.api.StepUpResponse
import javax.inject.Inject
import javax.inject.Singleton

// The app's own scheme: naming it is what has the server hand the step-up's
// proof back to the app rather than to the browser that finished the consent.
private const val OAUTH_SCHEME = "mochi"

/**
 * Settings' [StepUpClient]: the shared StepUpDialog cannot name app action
 * paths, so the app supplies the verify calls (mirrors web's
 * step-up-client.ts).
 */
@Singleton
class SettingsStepUpClient @Inject constructor(
    private val api: AccountApi,
    private val passkeyManager: PasskeyManager,
    // Hilt hands every holder the one the app's deep links deliver to.
    private val returns: OAuthVerifyReturn = OAuthVerifyReturn(),
) : StepUpClient {

    // One step-up runs at a time; the OAuth verifier the dialog began with is
    // held here for the poll, with whether its proof returns to the app.
    private var oauthVerifier: String? = null
    private var oauthNative = false

    private suspend fun states(): MethodsResponse = api.getMethods().unwrapRaw()

    // The required step-up factors. The server names them: a run of wrong
    // authenticator codes adds the emailed code, which no method's state
    // shows. A server that does not name them is read from the states: those
    // "required", recovery excluded.
    private fun requiredFactors(states: MethodsResponse): List<String> =
        states.required?.distinct()
            ?: states.methods.filter { it.value.state == "required" }.keys.filter { it != "recovery" }

    override suspend fun methods(): List<String> {
        val states = states()
        val map = states.methods
        val required = requiredFactors(states)
        // OAuth is offered separately via oauthProviders, never as a code
        // field, so drop it from the code-factor list.
        if (required.isNotEmpty()) return required.filter { it != "oauth" }
        // Nothing required: offer every usable code/credential factor so any
        // one satisfies the step-up.
        return listOf("email", "passkey", "totp").filter {
            map[it] != null && map[it]!!.state != "disabled"
        }
    }

    override suspend fun send() {
        api.sendCode().unwrapRaw()
    }

    override suspend fun verifyEmail(code: String): StepUpResult =
        api.verifyCode(code).unwrapRaw().toResult()

    override suspend fun verifyTotp(code: String): StepUpResult =
        api.verifyTotp(code).unwrapRaw().toResult()

    override suspend fun passkey(): StepUpResult {
        val begin = api.beginPasskeyVerify().unwrapRaw()
        if (begin.ceremony.isBlank()) throw RuntimeException("missing ceremony")
        val assertion = passkeyManager.authenticateRaw(begin.options)
        return api.finishPasskeyVerify(begin.ceremony, assertion).unwrapRaw().toResult()
    }

    override suspend fun oauthProviders(): List<String> {
        val states = states()
        val map = states.methods
        val required = requiredFactors(states)
        // OAuth re-verifies the oauth factor: acceptable only when oauth is
        // required, or nothing is required (any one factor) and the user
        // hasn't disabled it. A required email factor needs a real email code.
        val oauth = map["oauth"]
        val acceptable = required.contains("oauth") ||
            (required.isEmpty() && oauth != null && oauth.state != "disabled")
        if (!acceptable) return emptyList()
        // Only the providers the server can confirm an identity through.
        return api.listOAuth().unwrapRaw().identities.filter { it.verify != false }.map { it.provider }.distinct()
    }

    override suspend fun oauthBegin(provider: String): String {
        val verifier = OAuthPkce.generateVerifier()
        oauthVerifier = verifier
        val challenge = OAuthPkce.challengeFor(verifier)
        val begun = api.beginOauthVerify(provider, challenge, OAUTH_SCHEME).unwrapRaw()
        oauthNative = !begun.nonce.isNullOrBlank()
        returns.expect(begun.nonce.orEmpty())
        return begun.url
    }

    override suspend fun oauthPoll(): StepUpResult {
        val verifier = oauthVerifier ?: return StepUpResult()
        // A server that answered no nonce proves the consent where the browser
        // finished it, so the proof is polled for; otherwise the proof comes
        // back to the app as a code, and nothing is asked until it does.
        if (!oauthNative) return api.finishOauthVerify(verifier, null).unwrapRaw().toResult()
        val (code, error) = returns.take() ?: return StepUpResult()
        if (error != null || code == null) throw MochiError.Unknown()
        val result = api.finishOauthVerify(verifier, code).unwrapRaw().toResult()
        // The code is spent either way: one the server would not redeem leaves
        // nothing to wait for.
        if (result.token == null && result.remaining == null) throw MochiError.Unknown()
        return result
    }

    private fun StepUpResponse.toResult() = StepUpResult(token = token, remaining = remaining)
}
