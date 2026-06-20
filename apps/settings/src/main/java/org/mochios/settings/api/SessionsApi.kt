// Copyright © 2026 Mochi OÜ
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
)

data class SessionsResponse(val sessions: List<Session> = emptyList())

interface SessionsApi {
    @GET("settings/-/user/account/sessions")
    suspend fun listSessions(): Response<SessionsResponse>

    @FormUrlEncoded
    @POST("settings/-/user/account/session/revoke")
    suspend fun revokeSession(@Field("id") id: String): Response<OkResponse>
}

@Module
@InstallIn(SingletonComponent::class)
object SessionsApiModule {
    @Provides
    @Singleton
    fun provideSessionsApi(@SettingsRetrofit retrofit: Retrofit): SessionsApi =
        retrofit.create(SessionsApi::class.java)
}
