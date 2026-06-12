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
    val name: String = ""
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
     *  token in the request (Authorization header). */
    val link: Boolean = false,
    /** Mobile target the server redirects back to with `?oauth_linked=` or
     *  `?oauth_error=`. Should be a `<scheme>://oauth-link-return` URI. */
    val target: String = ""
)

data class OAuthBeginResponse(
    val url: String = ""
)

data class OAuthExchangeRequest(
    val code: String,
    val verifier: String
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
}
