// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.auth

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class BeginResponse(
    val allowed: List<String> = emptyList(),
    @SerializedName("has_passkey") val hasPasskey: Boolean = false,
    val oauth: Boolean = false
)

data class CodeResponse(
    val success: Boolean = false
)

data class VerifyResponse(
    @SerializedName("has_identity") val hasIdentity: Boolean = true,
    val name: String? = null,
    val mfa: Boolean? = null,
    val partial: String? = null,
    val remaining: List<String>? = null
)

data class PasskeyBeginResponse(
    val options: JsonObject = JsonObject(),
    val ceremony: String = ""
)

data class IdentityResponse(
    val success: Boolean = false
)

data class IdentityInfoResponse(
    val user: IdentityUserNode = IdentityUserNode(),
    val identity: IdentityEntityNode? = null
)

data class IdentityUserNode(
    val email: String = "",
    val name: String = "",
    /** Account lifecycle status. "closing" means the account is soft-deleted
     *  and pending purge; the app routes such sessions to the reactivation
     *  interstitial. */
    val status: String = "",
    /** Unix-seconds purge deadline when [status] is "closing". */
    val purge: Long = 0
)

data class IdentityEntityNode(
    val id: String = "",
    val name: String = "",
    val privacy: String = "",
    val fingerprint: String = ""
)

data class MethodsResponse(
    val email: Boolean = false,
    val passkey: Boolean = false,
    val recovery: Boolean = false,
    val signup: Boolean = false,
    val oauth: Map<String, Boolean> = emptyMap()
)

data class OAuthBeginRequest(
    val mode: String = "mobile",
    val scheme: String,
    val challenge: String,
    /** When true, server attaches the OAuth identity to the current session's
     *  user instead of starting a sign-in. Requires an authenticated Bearer
     *  token in the request (Authorization header) AND a step-up proof in
     *  [token]: linking adds a way to sign in, so a session alone is not
     *  enough. */
    val link: Boolean = false,
    /** Where the server should send the browser after a link. Currently
     *  DISCARDED for a custom scheme: core runs it through redirect_local,
     *  which keeps only paths starting with a single "/". No deep link comes
     *  back, and this client no longer listens for one. */
    val target: String = "",
    /** Step-up re-authentication proof, required by the server when [link] is
     *  true and ignored otherwise (signing in is not a credential change). */
    val token: String = ""
)

data class OAuthBeginResponse(
    val url: String = "",
    /**
     * Single-use value the server echoes on the deep-link return, so this
     * client can tell its own return from one delivered by another app.
     * Mobile mode only, and null against a server that predates it.
     */
    val nonce: String? = null
)

data class OAuthExchangeRequest(
    val code: String,
    val verifier: String
)

/** What the exchange returns for a LINK ceremony: the provider now attached. */
data class OAuthLinkResponse(
    val linked: String = ""
)

// Request bodies
data class EmailRequest(val email: String)
data class CodeRequest(val code: String)
data class TotpRequest(val email: String, val code: String)
data class MfaRequest(
    val partial: String,
    @SerializedName("email_code") val emailCode: String? = null,
    @SerializedName("totp_code") val totpCode: String? = null
)
data class PasskeyFinishRequest(
    val ceremony: String,
    val id: String,
    val rawId: String,
    val type: String,
    val response: PasskeyResponseData
)
data class PasskeyResponseData(
    val clientDataJSON: String,
    val authenticatorData: String,
    val signature: String
)
data class IdentityRequest(val name: String, val privacy: String = "public")
data class RecoveryRequest(val username: String, val code: String)

interface AuthApi {

    @POST("_/auth/begin")
    suspend fun begin(@Body body: EmailRequest): Response<BeginResponse>

    @POST("_/auth/code")
    suspend fun requestCode(@Body body: EmailRequest): Response<CodeResponse>

    @POST("_/auth/verify")
    suspend fun verify(@Body body: CodeRequest): Response<VerifyResponse>

    @POST("_/auth/totp")
    suspend fun verifyTotp(@Body body: TotpRequest): Response<VerifyResponse>

    @POST("_/auth/methods")
    suspend fun completeMfa(@Body body: MfaRequest): Response<VerifyResponse>

    @POST("_/auth/passkey/begin")
    suspend fun passkeyBegin(): Response<PasskeyBeginResponse>

    @POST("_/auth/passkey/finish")
    suspend fun passkeyFinish(@Body body: PasskeyFinishRequest): Response<VerifyResponse>

    @POST("_/identity")
    suspend fun createIdentity(@Body body: IdentityRequest): Response<IdentityResponse>

    @GET("_/identity")
    suspend fun getIdentity(): Response<IdentityInfoResponse>

    @GET("_/auth/methods")
    suspend fun getAvailableMethods(): Response<MethodsResponse>

    @POST("_/auth/recovery")
    suspend fun verifyRecoveryCode(@Body body: RecoveryRequest): Response<VerifyResponse>

    @POST("_/auth/oauth/{provider}/begin")
    suspend fun oauthBegin(
        @retrofit2.http.Path("provider") provider: String,
        @Body body: OAuthBeginRequest
    ): Response<OAuthBeginResponse>

    @POST("_/auth/oauth/{provider}/begin")
    suspend fun oauthBeginAuthorised(
        @retrofit2.http.Path("provider") provider: String,
        @retrofit2.http.Header("Authorization") authorization: String,
        @Body body: OAuthBeginRequest
    ): Response<OAuthBeginResponse>

    @POST("_/auth/oauth/exchange")
    suspend fun oauthExchange(@Body body: OAuthExchangeRequest): Response<VerifyResponse>

    /**
     * Exchange for a LINK ceremony. The Bearer is not optional here: the
     * server writes the identity link only for the user the token names, and
     * the browser that carried the callback proved nothing about who that is.
     */
    @POST("_/auth/oauth/exchange")
    suspend fun oauthExchangeLink(
        @retrofit2.http.Header("Authorization") authorization: String,
        @Body body: OAuthExchangeRequest
    ): Response<OAuthLinkResponse>

    /** Cancel a pending self-service closure, reactivating the account. */
    @POST("_/auth/close/cancel")
    suspend fun cancelClose(): Response<Unit>
}
