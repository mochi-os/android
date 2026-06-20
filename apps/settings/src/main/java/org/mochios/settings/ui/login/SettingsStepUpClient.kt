// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.login

import org.mochios.android.auth.OAuthPkce
import org.mochios.android.auth.PasskeyManager
import org.mochios.android.auth.StepUpClient
import org.mochios.android.auth.StepUpResult
import org.mochios.settings.api.AccountApi
import org.mochios.settings.api.MethodInfo
import org.mochios.settings.api.StepUpResponse
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wires the shared StepUpDialog to the settings app's own re-authentication
 * actions (mochi.user.code.verify / totp.verify / passkey.verify /
 * oauth.verify). Mirrors apps/settings/web/src/lib/step-up-client.ts; a lib
 * component can't reference these action paths itself, so the app injects this.
 */
@Singleton
class SettingsStepUpClient @Inject constructor(
    private val api: AccountApi,
    private val passkeyManager: PasskeyManager,
) : StepUpClient {

    // One step-up runs at a time; the OAuth verifier the dialog began with is
    // held here for the poll.
    private var oauthVerifier: String? = null

    private suspend fun states(): Map<String, MethodInfo> =
        api.getMethods().bodyOrThrow().methods

    // The required step-up factors, mirroring reauthentication_required: the
    // methods whose state is "required", recovery excluded.
    private fun requiredFactors(map: Map<String, MethodInfo>): List<String> =
        map.filter { it.value.state == "required" }.keys.filter { it != "recovery" }

    override suspend fun methods(): List<String> {
        val map = states()
        val required = requiredFactors(map)
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
        api.sendCode().bodyOrThrow()
    }

    override suspend fun verifyEmail(code: String): StepUpResult =
        api.verifyCode(code).bodyOrThrow().toResult()

    override suspend fun verifyTotp(code: String): StepUpResult =
        api.verifyTotp(code).bodyOrThrow().toResult()

    override suspend fun passkey(): StepUpResult {
        val begin = api.beginPasskeyVerify().bodyOrThrow()
        if (begin.ceremony.isBlank()) throw RuntimeException("missing ceremony")
        val assertion = passkeyManager.authenticateRaw(begin.options)
        return api.finishPasskeyVerify(begin.ceremony, assertion).bodyOrThrow().toResult()
    }

    override suspend fun oauthProviders(): List<String> {
        val map = states()
        val required = requiredFactors(map)
        // OAuth re-verifies the oauth factor: acceptable only when oauth is
        // required, or nothing is required (any one factor) and the user
        // hasn't disabled it. A required email factor needs a real email code.
        val oauth = map["oauth"]
        val acceptable = required.contains("oauth") ||
            (required.isEmpty() && oauth != null && oauth.state != "disabled")
        if (!acceptable) return emptyList()
        return api.listOAuth().bodyOrThrow().identities.map { it.provider }.distinct()
    }

    override suspend fun oauthBegin(provider: String): String {
        val verifier = OAuthPkce.generateVerifier()
        oauthVerifier = verifier
        val challenge = OAuthPkce.challengeFor(verifier)
        return api.beginOauthVerify(provider, challenge).bodyOrThrow().url
    }

    override suspend fun oauthPoll(): StepUpResult {
        val verifier = oauthVerifier ?: return StepUpResult()
        return api.finishOauthVerify(verifier).bodyOrThrow().toResult()
    }

    private fun StepUpResponse.toResult() = StepUpResult(token = token, remaining = remaining)

    private fun <T> Response<T>.bodyOrThrow(): T {
        if (!isSuccessful) throw RuntimeException("HTTP ${code()}")
        return body() ?: throw RuntimeException("empty body")
    }
}
