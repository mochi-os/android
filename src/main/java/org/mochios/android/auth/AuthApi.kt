package org.mochi.android.auth

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class BeginResponse(
    val methods: List<String> = emptyList(),
    @SerializedName("has_passkey") val hasPasskey: Boolean = false,
    val new: Boolean = false
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

data class TokenResponse(
    val token: String = ""
)

data class IdentityResponse(
    val success: Boolean = false
)

data class IdentityInfoResponse(
    val user: Int = 0,
    val name: String = "",
    val fingerprint: String = ""
)

data class MethodsResponse(
    val methods: List<String> = emptyList()
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
data class TokenRequest(val app: String)
data class IdentityRequest(val name: String)
data class RecoveryRequest(val email: String, val code: String)

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

    @POST("_/token")
    suspend fun fetchToken(@Body body: TokenRequest): Response<TokenResponse>

    @POST("_/identity")
    suspend fun createIdentity(@Body body: IdentityRequest): Response<IdentityResponse>

    @GET("_/identity")
    suspend fun getIdentity(): Response<IdentityInfoResponse>

    @GET("_/auth/methods")
    suspend fun getAvailableMethods(): Response<MethodsResponse>

    @POST("_/auth/recovery")
    suspend fun verifyRecoveryCode(@Body body: RecoveryRequest): Response<VerifyResponse>
}
