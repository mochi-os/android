// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.auth

import org.mochios.android.api.unwrapRaw
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthResult {
    data object Success : AuthResult()
    data object NeedsIdentity : AuthResult()
    data class NeedsMfa(
        val partial: String,
        val remaining: List<String>
    ) : AuthResult()
}

data class BeginResult(
    val allowed: List<String>,
    val hasPasskey: Boolean,
    val oauth: Boolean
)

data class PasskeyChallenge(
    val options: com.google.gson.JsonObject,
    val ceremony: String
)

data class Identity(
    val identity: String,    // canonical entity ID, network-unique
    val name: String,
    val email: String,
    val fingerprint: String
)

/** Account lifecycle status plus the purge deadline used by the reactivation
 *  interstitial. */
data class AccountStatus(
    val status: String,
    val purge: Long
)

/** Everything a single `_/identity` fetch yields — the bound identity entity
 *  and the account lifecycle status. Lets callers that need both (bootstrap)
 *  avoid issuing the same request twice. */
data class IdentityInfo(
    val identity: Identity,
    val status: AccountStatus
)

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val tokenApi: TokenApi,
    private val sessionManager: SessionManager
) {
    suspend fun beginLogin(email: String): BeginResult {
        val response = authApi.begin(EmailRequest(email)).unwrapRaw()
        return BeginResult(
            allowed = response.allowed,
            hasPasskey = response.hasPasskey,
            oauth = response.oauth
        )
    }

    suspend fun requestCode(email: String) {
        authApi.requestCode(EmailRequest(email)).unwrapRaw()
    }

    suspend fun verifyCode(code: String): AuthResult {
        val response = authApi.verify(CodeRequest(code))
        extractSessionCookie(response)
        val data = response.unwrapRaw()
        return mapVerifyResponse(data)
    }

    suspend fun verifyTotp(email: String, code: String): AuthResult {
        val response = authApi.verifyTotp(TotpRequest(email, code))
        extractSessionCookie(response)
        val data = response.unwrapRaw()
        return mapVerifyResponse(data)
    }

    suspend fun completeMfa(partial: String, emailCode: String?, totpCode: String?): AuthResult {
        val response = authApi.completeMfa(MfaRequest(partial, emailCode, totpCode))
        extractSessionCookie(response)
        val data = response.unwrapRaw()
        return mapVerifyResponse(data)
    }

    suspend fun beginPasskey(): PasskeyChallenge {
        val response = authApi.passkeyBegin().unwrapRaw()
        return PasskeyChallenge(
            options = response.options,
            ceremony = response.ceremony
        )
    }

    suspend fun finishPasskey(
        ceremony: String,
        id: String,
        rawId: String,
        type: String,
        clientDataJSON: String,
        authenticatorData: String,
        signature: String
    ): AuthResult {
        val response = authApi.passkeyFinish(
            PasskeyFinishRequest(
                ceremony = ceremony,
                id = id,
                rawId = rawId,
                type = type,
                response = PasskeyResponseData(
                    clientDataJSON = clientDataJSON,
                    authenticatorData = authenticatorData,
                    signature = signature
                )
            )
        )
        extractSessionCookie(response)
        val data = response.unwrapRaw()
        return mapVerifyResponse(data)
    }

    suspend fun verifyRecoveryCode(username: String, code: String): AuthResult {
        val response = authApi.verifyRecoveryCode(RecoveryRequest(username, code))
        extractSessionCookie(response)
        val data = response.unwrapRaw()
        return mapVerifyResponse(data)
    }

    suspend fun fetchToken(app: String): Result<String> {
        return runCatching {
            val response = tokenApi.fetchToken(TokenRequest(app)).unwrapRaw()
            sessionManager.saveToken(app, response.token)
            response.token
        }
    }

    suspend fun createIdentity(name: String, privacy: String = "public") {
        authApi.createIdentity(IdentityRequest(name, privacy)).unwrapRaw()
    }

    /** Single `_/identity` round-trip yielding both the bound identity and the
     *  account status. Prefer this when you need both (e.g. bootstrap) so the
     *  request isn't issued twice. */
    suspend fun getIdentityInfo(): IdentityInfo {
        val response = authApi.getIdentity().unwrapRaw()
        return IdentityInfo(
            identity = Identity(
                identity = response.identity?.id.orEmpty(),
                name = response.identity?.name?.takeIf { it.isNotBlank() }
                    ?: response.user.name,
                email = response.user.email,
                fingerprint = response.identity?.fingerprint.orEmpty()
            ),
            status = AccountStatus(response.user.status, response.user.purge)
        )
    }

    /** Cancel a pending closure, reactivating the account. */
    suspend fun cancelClose() {
        authApi.cancelClose().unwrapRaw()
    }

    suspend fun getAvailableMethods(): MethodsResponse {
        return authApi.getAvailableMethods().unwrapRaw()
    }

    suspend fun beginOAuth(provider: String, scheme: String, challenge: String): String {
        val resp = authApi.oauthBegin(
            provider,
            OAuthBeginRequest(mode = "mobile", scheme = scheme, challenge = challenge)
        ).unwrapRaw()
        return resp.url
    }

    /**
     * Begin an OAuth flow that LINKS the provider identity to the
     * already-authenticated user instead of starting a sign-in. The server
     * needs a Bearer JWT for any of the user's apps; any settings/feeds/...
     * token works since the JWT verification is core-level.
     *
     * Server redirects to `<target>?oauth_linked=<provider>` on success or
     * `<target>?oauth_error=<reason>` on failure. The host MainActivity
     * parses the return URI and routes into `SessionManager.setOAuthLinkReturn`.
     */
    suspend fun beginOAuthLink(
        provider: String,
        scheme: String,
        target: String,
        challenge: String,
        bearerToken: String,
    ): String {
        val resp = authApi.oauthBeginAuthorised(
            provider,
            "Bearer $bearerToken",
            OAuthBeginRequest(
                mode = "mobile",
                scheme = scheme,
                challenge = challenge,
                link = true,
                target = target,
            )
        ).unwrapRaw()
        return resp.url
    }

    suspend fun exchangeOAuth(code: String, verifier: String): AuthResult {
        val response = authApi.oauthExchange(OAuthExchangeRequest(code, verifier))
        extractSessionCookie(response)
        val data = response.unwrapRaw()
        return mapVerifyResponse(data)
    }

    private fun mapVerifyResponse(data: VerifyResponse): AuthResult {
        if (data.mfa == true && data.partial != null && data.remaining != null) {
            return AuthResult.NeedsMfa(
                partial = data.partial,
                remaining = data.remaining
            )
        }
        if (!data.hasIdentity) {
            return AuthResult.NeedsIdentity
        }
        return AuthResult.Success
    }

    private fun <T> extractSessionCookie(response: retrofit2.Response<T>) {
        val setCookie = response.headers().values("Set-Cookie")
        for (cookie in setCookie) {
            if (cookie.startsWith("session=")) {
                val value = cookie.substringAfter("session=").substringBefore(";")
                kotlinx.coroutines.runBlocking {
                    sessionManager.saveSession(value)
                }
                break
            }
        }
    }
}
