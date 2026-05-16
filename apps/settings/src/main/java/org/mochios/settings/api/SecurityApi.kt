package org.mochios.settings.api

import com.google.gson.annotations.SerializedName
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.mochios.settings.ui.profile.SettingsRetrofit
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import javax.inject.Singleton

// ---------- DTOs ----------

data class MethodsResponse(val methods: List<String> = emptyList())

data class Passkey(
    val id: String = "",
    val name: String = "",
    val transports: List<String> = emptyList(),
    val created: Long = 0,
    @SerializedName("last_used") val lastUsed: Long = 0,
)

data class PasskeysResponse(val passkeys: List<Passkey> = emptyList())

/** Server returns the WebAuthn ceremony options plus an opaque ceremony token
 *  the client must echo back to `register/finish`. `options` is the
 *  PublicKeyCredentialCreationOptions JSON the CredentialManager consumes. */
data class PasskeyRegisterBegin(
    val ceremony: String = "",
    val options: com.google.gson.JsonObject = com.google.gson.JsonObject(),
)

data class TotpStatus(val enabled: Boolean = false)

data class TotpSetupResponse(
    val secret: String = "",
    val url: String = "",
    val issuer: String = "",
    val domain: String = "",
)

data class OkResponse(val ok: Boolean = false)

data class RecoveryCountResponse(val count: Int = 0)

data class RecoveryGenerateResponse(val codes: List<String> = emptyList())

data class Session(
    val id: String = "",
    val address: String = "",
    val agent: String = "",
    val created: Long = 0,
    val accessed: Long = 0,
    val expires: Long = 0,
)

data class SessionsResponse(val sessions: List<Session> = emptyList())

data class OAuthIdentity(
    val provider: String = "",
    val email: String = "",
    val name: String = "",
    val created: Long = 0,
    val used: Long = 0,
)

data class OAuthIdentitiesResponse(val identities: List<OAuthIdentity> = emptyList())

data class ApiToken(
    val hash: String = "",
    val name: String = "",
    val scopes: String = "",
    val created: Long = 0,
    @SerializedName("last_used") val lastUsed: Long = 0,
    val expires: String = "",
)

data class TokensResponse(val tokens: List<ApiToken> = emptyList())

data class TokenCreateResponse(val token: String = "")

// ---------- Retrofit ----------

interface SecurityApi {
    @GET("settings/-/user/account/methods")
    suspend fun getMethods(): Response<MethodsResponse>

    @FormUrlEncoded
    @POST("settings/-/user/account/methods/set")
    suspend fun setMethods(@Field("methods") methodsCsv: String): Response<MethodsResponse>

    // Passkeys
    @GET("settings/-/user/account/passkeys")
    suspend fun listPasskeys(): Response<PasskeysResponse>

    @POST("settings/-/user/account/passkey/register/begin")
    suspend fun beginPasskeyRegister(): Response<PasskeyRegisterBegin>

    @FormUrlEncoded
    @POST("settings/-/user/account/passkey/register/finish")
    suspend fun finishPasskeyRegister(
        @Field("ceremony") ceremony: String,
        @Field("credential") credential: String,
        @Field("name") name: String,
    ): Response<OkResponse>

    @FormUrlEncoded
    @POST("settings/-/user/account/passkey/rename")
    suspend fun renamePasskey(
        @Field("id") id: String,
        @Field("name") name: String,
    ): Response<OkResponse>

    @FormUrlEncoded
    @POST("settings/-/user/account/passkey/delete")
    suspend fun deletePasskey(@Field("id") id: String): Response<OkResponse>

    // TOTP
    @GET("settings/-/user/account/totp")
    suspend fun getTotp(): Response<TotpStatus>

    @POST("settings/-/user/account/totp/setup")
    suspend fun setupTotp(): Response<TotpSetupResponse>

    @FormUrlEncoded
    @POST("settings/-/user/account/totp/verify")
    suspend fun verifyTotp(@Field("code") code: String): Response<OkResponse>

    @POST("settings/-/user/account/totp/disable")
    suspend fun disableTotp(): Response<OkResponse>

    // Recovery codes
    @GET("settings/-/user/account/recovery")
    suspend fun recoveryCount(): Response<RecoveryCountResponse>

    @POST("settings/-/user/account/recovery/generate")
    suspend fun generateRecovery(): Response<RecoveryGenerateResponse>

    // Sessions
    @GET("settings/-/user/account/sessions")
    suspend fun listSessions(): Response<SessionsResponse>

    @FormUrlEncoded
    @POST("settings/-/user/account/session/revoke")
    suspend fun revokeSession(@Field("id") id: String): Response<OkResponse>

    // OAuth identities
    @GET("settings/-/user/account/oauth")
    suspend fun listOAuth(): Response<OAuthIdentitiesResponse>

    @FormUrlEncoded
    @POST("settings/-/user/account/oauth/unlink")
    suspend fun unlinkOAuth(@Field("provider") provider: String): Response<OkResponse>

    // API tokens
    @GET("settings/-/user/account/tokens")
    suspend fun listTokens(): Response<TokensResponse>

    @FormUrlEncoded
    @POST("settings/-/user/account/token/create")
    suspend fun createToken(
        @Field("name") name: String,
        @Field("scopes") scopes: String? = null,
        @Field("expires") expires: String? = null,
    ): Response<TokenCreateResponse>

    @FormUrlEncoded
    @POST("settings/-/user/account/token/delete")
    suspend fun deleteToken(@Field("hash") hash: String): Response<OkResponse>
}

@Module
@InstallIn(SingletonComponent::class)
object SecurityApiModule {
    @Provides
    @Singleton
    fun provideSecurityApi(@SettingsRetrofit retrofit: Retrofit): SecurityApi =
        retrofit.create(SecurityApi::class.java)
}
