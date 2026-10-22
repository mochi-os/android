// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.api

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

data class Session(
    val id: String = "",
    val address: String = "",
    val agent: String = "",
    val created: Long = 0,
    val accessed: Long = 0,
    val expires: Long = 0,
    val current: Boolean = false,
)

/** The app a sign-in by another network's app came from. */
data class TokenClient(
    val id: String = "",
    val name: String = "",
    val website: String = "",
)

/**
 * A sign-in by another network's app, or a password minted for one, with the
 * account it is for.
 */
data class OtherToken(
    val hash: String = "",
    val name: String = "",
    val created: Long = 0,
    val used: Long = 0,
    val kind: String = "",
    val client: TokenClient = TokenClient(),
    val address: String = "",
    val network: String = "",
    val device: String = "",
)

data class SessionsResponse(
    val sessions: List<Session> = emptyList(),
    val signins: List<OtherToken> = emptyList(),
    val passwords: List<OtherToken> = emptyList(),
)

interface SessionsApi {
    @GET("settings/-/user/account/sessions")
    suspend fun listSessions(): Response<SessionsResponse>

    @FormUrlEncoded
    @POST("settings/-/user/account/session/revoke")
    suspend fun revokeSession(@Field("id") id: String): Response<OkResponse>

    @FormUrlEncoded
    @POST("settings/-/user/account/token/revoke")
    suspend fun revokeToken(@Field("hash") hash: String): Response<OkResponse>
}

@Module
@InstallIn(SingletonComponent::class)
object SessionsApiModule {
    @Provides
    @Singleton
    fun provideSessionsApi(@SettingsRetrofit retrofit: Retrofit): SessionsApi =
        retrofit.create(SessionsApi::class.java)
}
