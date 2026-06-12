package org.mochios.settings.api

import com.google.gson.annotations.SerializedName
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import javax.inject.Singleton

// ---------- Shared ----------

/** Generic `{ "ok": true }` envelope used by mutate endpoints across all
 *  settings APIs. Lives here because AccountApi defines the largest set of
 *  endpoints that return it; TokensApi and SessionsApi import from here. */
data class OkResponse(val ok: Boolean = false)

/** Step-up re-authentication result. A verify returns `token` once every
 *  required factor is satisfied, otherwise `remaining` lists the factors still
 *  outstanding. The TOTP verify endpoint doubles as the enrolment confirm and
 *  then returns `ok` instead. */
data class StepUpResponse(
    val token: String? = null,
    val remaining: List<String>? = null,
    val ok: Boolean? = null,
)

/** `{ "url": "..." }` envelope for the OAuth step-up begin action. */
data class UrlResponse(val url: String = "")

// ---------- Identity ----------

data class IdentityResponse(
    @SerializedName("entity") val entity: String = "",
    @SerializedName("fingerprint") val fingerprint: String = "",
    @SerializedName("username") val username: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("privacy") val privacy: String = "private",
    @SerializedName("role") val role: String = "",
)

// ---------- Login methods (per-method tri-state) ----------

/** Per-method state plus the operator policy and credential availability, so
 *  the UI can grey out options the user can't pick. Mirrors the web
 *  MethodInfo. `state`/`system` are one of disabled/allowed/required. */
data class MethodInfo(
    val state: String = "disabled",
    val system: String = "allowed",
    val available: Boolean = false,
)

data class MethodsResponse(val methods: Map<String, MethodInfo> = emptyMap())

// ---------- Passkeys ----------

data class Passkey(
    val id: String = "",
    val name: String = "",
    val transports: List<String> = emptyList(),
    val created: Long = 0,
    @SerializedName("last_used") val lastUsed: Long = 0,
)

data class PasskeysResponse(val passkeys: List<Passkey> = emptyList())

/** Server returns the WebAuthn ceremony options plus an opaque ceremony token
 *  the client must echo back to `register/finish` (or `verify/finish`).
 *  `options` is the PublicKey...Options JSON the CredentialManager consumes. */
data class PasskeyCeremony(
    val ceremony: String = "",
    val options: com.google.gson.JsonObject = com.google.gson.JsonObject(),
)

// ---------- TOTP ----------

data class TotpStatus(val enabled: Boolean = false)

data class TotpSetupResponse(
    val secret: String = "",
    val url: String = "",
    val issuer: String = "",
    val domain: String = "",
)

// ---------- Recovery codes ----------

data class RecoveryCountResponse(val count: Int = 0)

data class RecoveryGenerateResponse(val codes: List<String> = emptyList())

// ---------- OAuth identities ----------

data class OAuthIdentity(
    val provider: String = "",
    val email: String = "",
    val name: String = "",
    val created: Long = 0,
    val used: Long = 0,
)

data class OAuthIdentitiesResponse(val identities: List<OAuthIdentity> = emptyList())

/** Self-service account closure result: the unix-seconds timestamp at which
 *  the soft-deleted account will be hard-purged. */
data class CloseResponse(val purge: Long = 0)

/** Data-export build result: the server-side on-disk filename of the bundle,
 *  which the client then streams via the public export/download action. */
data class ExportResponse(val filename: String = "")

// ---------- Retrofit ----------

interface AccountApi {
    // Identity
    @GET("settings/-/user/account/identity")
    suspend fun getIdentity(): Response<IdentityResponse>

    @FormUrlEncoded
    @POST("settings/-/user/account/identity/update")
    suspend fun updateIdentity(
        @Field("name") name: String? = null,
        @Field("privacy") privacy: String? = null,
    ): Response<Map<String, Any>>

    // Login methods
    @GET("settings/-/user/account/methods")
    suspend fun getMethods(): Response<MethodsResponse>

    /** Set one method's state. Gated on step-up re-authentication. */
    @FormUrlEncoded
    @POST("settings/-/user/account/methods/set")
    suspend fun configureMethod(
        @Field("token") token: String,
        @Field("method") method: String,
        @Field("state") state: String,
    ): Response<OkResponse>

    // Passkeys
    @GET("settings/-/user/account/passkeys")
    suspend fun listPasskeys(): Response<PasskeysResponse>

    @POST("settings/-/user/account/passkey/register/begin")
    suspend fun beginPasskeyRegister(): Response<PasskeyCeremony>

    /** Complete passkey registration. Gated on step-up re-authentication. */
    @FormUrlEncoded
    @POST("settings/-/user/account/passkey/register/finish")
    suspend fun finishPasskeyRegister(
        @Field("token") token: String,
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

    /** Begin authenticator enrolment. Gated on step-up re-authentication. */
    @FormUrlEncoded
    @POST("settings/-/user/account/totp/setup")
    suspend fun setupTotp(@Field("token") token: String): Response<TotpSetupResponse>

    /** Confirm enrolment (returns ok) or re-verify as a step-up factor
     *  (returns token/remaining). */
    @FormUrlEncoded
    @POST("settings/-/user/account/totp/verify")
    suspend fun verifyTotp(@Field("code") code: String): Response<StepUpResponse>

    @FormUrlEncoded
    @POST("settings/-/user/account/totp/disable")
    suspend fun disableTotp(@Field("token") token: String): Response<OkResponse>

    // Recovery codes
    @GET("settings/-/user/account/recovery")
    suspend fun recoveryCount(): Response<RecoveryCountResponse>

    @FormUrlEncoded
    @POST("settings/-/user/account/recovery/generate")
    suspend fun generateRecovery(@Field("token") token: String): Response<RecoveryGenerateResponse>

    // OAuth identities
    @GET("settings/-/user/account/oauth")
    suspend fun listOAuth(): Response<OAuthIdentitiesResponse>

    @FormUrlEncoded
    @POST("settings/-/user/account/oauth/unlink")
    suspend fun unlinkOAuth(@Field("provider") provider: String): Response<OkResponse>

    /** Close the user's own account (soft delete + grace period). Gated on
     *  step-up re-authentication. Returns the purge timestamp. */
    @FormUrlEncoded
    @POST("settings/-/user/account/close")
    suspend fun closeAccount(@Field("token") token: String): Response<CloseResponse>

    /** Build a complete, restorable backup bundle (user data plus the
     *  passphrase-encrypted private keys). Gated on step-up re-authentication.
     *  Returns the on-disk filename; the bundle bytes are fetched separately
     *  via the public export/download action so multi-GB files are never
     *  buffered in memory. */
    @FormUrlEncoded
    @POST("settings/-/user/account/export")
    suspend fun exportData(
        @Field("token") token: String,
        @Field("passphrase") passphrase: String,
    ): Response<ExportResponse>

    // ---------- Step-up re-authentication ----------

    /** Email a step-up code. */
    @POST("settings/-/user/account/code")
    suspend fun sendCode(): Response<OkResponse>

    @FormUrlEncoded
    @POST("settings/-/user/account/code/verify")
    suspend fun verifyCode(@Field("code") code: String): Response<StepUpResponse>

    @POST("settings/-/user/account/passkey/verify/begin")
    suspend fun beginPasskeyVerify(): Response<PasskeyCeremony>

    @FormUrlEncoded
    @POST("settings/-/user/account/passkey/verify/finish")
    suspend fun finishPasskeyVerify(
        @Field("ceremony") ceremony: String,
        @Field("assertion") assertion: String,
    ): Response<StepUpResponse>

    @FormUrlEncoded
    @POST("settings/-/user/account/oauth/verify/begin")
    suspend fun beginOauthVerify(
        @Field("provider") provider: String,
        @Field("challenge") challenge: String,
    ): Response<UrlResponse>

    @FormUrlEncoded
    @POST("settings/-/user/account/oauth/verify/finish")
    suspend fun finishOauthVerify(@Field("verifier") verifier: String): Response<StepUpResponse>
}

@Module
@InstallIn(SingletonComponent::class)
object AccountApiModule {
    @Provides
    @Singleton
    fun provideAccountApi(@SettingsRetrofit retrofit: Retrofit): AccountApi =
        retrofit.create(AccountApi::class.java)
}
