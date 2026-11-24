// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.auth

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mochios.android.api.unwrapRaw
import java.util.concurrent.ConcurrentHashMap
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

    /**
     * The app's JWT: the cached one while it has more than [Token.MARGIN] of
     * life left, otherwise a fresh mint. [fresh] forces the mint - the
     * bootstrap uses the mint's 401 as its session-liveness probe. A cached
     * token dies with its session, and the sign-out that follows clears it.
     */
    suspend fun fetchToken(app: String, fresh: Boolean = false): Result<String> {
        if (!fresh) {
            val cached = sessionManager.getToken(app)
            if (cached != null && Token.fresh(cached, System.currentTimeMillis())) {
                return Result.success(cached)
            }
        }
        return runCatching {
            val session = sessionManager.currentToken.first()
            val response = tokenApi.fetchToken(Token.request(app)).unwrapRaw()
            sessionManager.saveToken(app, response.token)
            hold(app, session, response.asset)
            response.token
        }
    }

    /**
     * [app]'s asset token, for the images, media and files the app shows from
     * the user's server: the one held while it has more than
     * [ASSET_MARGIN] of its half hour left and was minted for the session
     * held now, otherwise a fresh mint. Null when there is no session, the
     * mint fails, or the server issues none. Held in memory only: it lives
     * half an hour. One mint at a time per app, so a screen of images asks
     * once.
     */
    suspend fun asset(app: String): String? {
        val session = sessionManager.currentToken.first() ?: return null
        held(app, session)?.let { held -> return held.token.ifEmpty { null } }
        return minting.computeIfAbsent(app) { Mutex() }.withLock {
            held(app, session)?.let { held -> return@withLock held.token.ifEmpty { null } }
            val response = try {
                tokenApi.fetchToken(Token.request(app)).unwrapRaw()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return@withLock null
            }
            hold(app, session, response.asset)
            held(app, session)?.token?.ifEmpty { null }
        }
    }

    /** [asset] for the asset client's threads, which have no coroutine. */
    fun assetBlocking(app: String): String? = runBlocking { asset(app) }

    /** What [app] holds for [session] with life left: an asset token, or the answer that there is none. */
    private fun held(app: String, session: String): HeldAsset? =
        assets[app]?.takeIf { held -> held.session == session && held.until - System.currentTimeMillis() > ASSET_MARGIN }

    private fun hold(app: String, session: String?, asset: String) {
        if (session == null) return
        // A server that issues none answers the same until a token would have
        // ended: asked again, every image of the app would wait on a mint.
        val until = if (asset.isBlank()) System.currentTimeMillis() + ASSET_LIFETIME else Token.expiry(asset) ?: return
        assets[app] = HeldAsset(session, asset.ifBlank { "" }, until)
    }

    /** An asset token, empty for none, the session it was minted for, and until when it stands. */
    private class HeldAsset(val session: String, val token: String, val until: Long)

    private val assets = ConcurrentHashMap<String, HeldAsset>()

    private val minting = ConcurrentHashMap<String, Mutex>()

    /**
     * Creates the signed-in user's identity and drops the held
     * [getIdentityInfo] answer, which still says there is none.
     */
    suspend fun createIdentity(name: String, privacy: String = "public") {
        try {
            authApi.createIdentity(IdentityRequest(name, privacy)).unwrapRaw()
        } finally {
            heldIdentity = null
        }
    }

    /**
     * The signed-in identity and the account status, from one `_/identity`
     * round-trip. A read for the same server and session within
     * [IDENTITY_LIFETIME] is answered from the last one: launch asks for it in
     * the app's bootstrap and again for the home menu. Calls that change
     * either half drop the held answer.
     */
    suspend fun getIdentityInfo(): IdentityInfo {
        val key = sessionManager.server.value + "|" +
            sessionManager.currentToken.first().orEmpty()
        val held = heldIdentity
        if (held != null && held.key == key &&
            SystemClock.elapsedRealtime() - held.at < IDENTITY_LIFETIME
        ) {
            return held.info
        }
        val info = readIdentityInfo()
        heldIdentity = HeldIdentity(key, SystemClock.elapsedRealtime(), info)
        return info
    }

    /** The last [getIdentityInfo] answer, for which server and session, and when. */
    private class HeldIdentity(val key: String, val at: Long, val info: IdentityInfo)

    @Volatile
    private var heldIdentity: HeldIdentity? = null

    private suspend fun readIdentityInfo(): IdentityInfo {
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

    /**
     * Cancels a pending closure, reactivating the account. Drops the held
     * [getIdentityInfo] answer, whose status still says "closing": the
     * bootstrap that follows must read the account fresh, or the first
     * Reactivate tap leaves the user on the reactivation screen.
     */
    suspend fun cancelClose() {
        try {
            authApi.cancelClose().unwrapRaw()
        } finally {
            heldIdentity = null
        }
    }

    suspend fun getAvailableMethods(): MethodsResponse {
        return authApi.getAvailableMethods().unwrapRaw()
    }

    /**
     * Begin a sign-in ceremony. Hold the returned nonce; it is null against a
     * server that does not send one.
     */
    suspend fun beginOAuth(provider: String, scheme: String, challenge: String): OAuthBeginResponse {
        return authApi.oauthBegin(
            provider,
            OAuthBeginRequest(mode = "mobile", scheme = scheme, challenge = challenge)
        ).unwrapRaw()
    }

    /**
     * Begin an OAuth flow that links the provider identity to the current user.
     * Any app's Bearer works - JWT verification is core-level. The link is
     * written at [exchangeOAuthLink], not at the callback, so hold the verifier
     * and nonce.
     */
    suspend fun beginOAuthLink(
        provider: String,
        scheme: String,
        target: String,
        challenge: String,
        bearerToken: String,
        stepUpToken: String,
    ): OAuthBeginResponse {
        return authApi.oauthBeginAuthorised(
            provider,
            "Bearer $bearerToken",
            OAuthBeginRequest(
                mode = "mobile",
                scheme = scheme,
                challenge = challenge,
                link = true,
                target = target,
                token = stepUpToken,
            )
        ).unwrapRaw()
    }

    /**
     * Complete a grant ceremony: the verifier proves the app instance, the
     * Bearer the user. Returns the capability landed and its account.
     */
    suspend fun exchangeOAuthGrant(code: String, verifier: String, bearerToken: String): OAuthGrantResponse {
        return authApi.oauthExchangeGrant(
            "Bearer $bearerToken",
            OAuthExchangeRequest(code = code, verifier = verifier)
        ).unwrapRaw()
    }

    /**
     * Complete a link ceremony: the verifier proves the app instance, the
     * Bearer the user. Returns the provider linked.
     */
    suspend fun exchangeOAuthLink(code: String, verifier: String, bearerToken: String): String {
        return authApi.oauthExchangeLink(
            "Bearer $bearerToken",
            OAuthExchangeRequest(code, verifier)
        ).unwrapRaw().linked
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

    private suspend fun <T> extractSessionCookie(response: retrofit2.Response<T>) {
        val setCookie = response.headers().values("Set-Cookie")
        for (cookie in setCookie) {
            if (cookie.startsWith("session=")) {
                val value = cookie.substringAfter("session=").substringBefore(";")
                sessionManager.saveSession(value)
                break
            }
        }
    }

    private companion object {
        /** How long, in ms, an identity read stands for the same session. */
        const val IDENTITY_LIFETIME = 10_000L

        /** How much life, in ms, a held asset token must have left to be used. */
        const val ASSET_MARGIN = 5L * 60 * 1000

        /** How long, in ms, an asset token lives: how long an answer of none is held. */
        const val ASSET_LIFETIME = 30L * 60 * 1000
    }
}
