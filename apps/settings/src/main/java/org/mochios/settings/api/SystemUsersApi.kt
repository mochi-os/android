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
import retrofit2.http.POST
import javax.inject.Singleton

// Mirrors apps/settings/web/src/api/system-users.ts. Action handlers live in
// apps/settings/system/users.star and read fields with a.input(), so every
// request is x-www-form-urlencoded.

data class SystemUser(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("username") val username: String = "",
    @SerializedName("role") val role: String = "user",
    @SerializedName("status") val status: String = "active",
    @SerializedName("methods") val methods: String = "",
    @SerializedName("last") val last: Long = 0,
)

data class SystemUsersList(
    @SerializedName("users") val users: List<SystemUser> = emptyList(),
    @SerializedName("count") val count: Int = 0,
)

data class SystemUserSession(
    @SerializedName("id") val id: String = "",
    @SerializedName("expires") val expires: Long = 0,
    @SerializedName("created") val created: Long = 0,
    @SerializedName("accessed") val accessed: Long = 0,
    @SerializedName("address") val address: String = "",
    @SerializedName("agent") val agent: String = "",
)

data class SystemUserSessions(
    @SerializedName("sessions") val sessions: List<SystemUserSession> = emptyList(),
)

data class RevokeSessionsResponse(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("revoked") val revoked: Int = 0,
)

interface SystemUsersApi {
    @FormUrlEncoded
    @POST("settings/-/system/users/list")
    suspend fun list(
        @Field("limit") limit: Int,
        @Field("offset") offset: Int,
        @Field("search") search: String,
        @Field("sort") sort: String,
        @Field("order") order: String,
    ): Response<SystemUsersList>

    @FormUrlEncoded
    @POST("settings/-/system/users/create")
    suspend fun create(
        @Field("username") username: String,
        @Field("role") role: String,
    ): Response<SystemUser>

    @FormUrlEncoded
    @POST("settings/-/system/users/update")
    suspend fun update(
        @Field("id") id: Long,
        @Field("username") username: String?,
        @Field("role") role: String?,
    ): Response<Map<String, Any>>

    @FormUrlEncoded
    @POST("settings/-/system/users/delete")
    suspend fun delete(@Field("id") id: Long): Response<Map<String, Any>>

    @FormUrlEncoded
    @POST("settings/-/system/users/suspend")
    suspend fun suspendUser(@Field("id") id: Long): Response<Map<String, Any>>

    @FormUrlEncoded
    @POST("settings/-/system/users/activate")
    suspend fun activate(@Field("id") id: Long): Response<Map<String, Any>>

    @FormUrlEncoded
    @POST("settings/-/system/users/sessions")
    suspend fun sessions(@Field("id") id: Long): Response<SystemUserSessions>

    @FormUrlEncoded
    @POST("settings/-/system/users/sessions/revoke")
    suspend fun revokeSessions(
        @Field("id") id: Long,
        @Field("session_id") sessionId: String?,
    ): Response<RevokeSessionsResponse>
}

@Module
@InstallIn(SingletonComponent::class)
object SystemUsersApiModule {
    @Provides
    @Singleton
    fun provideSystemUsersApi(@SettingsRetrofit retrofit: Retrofit): SystemUsersApi =
        retrofit.create(SystemUsersApi::class.java)
}
