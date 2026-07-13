// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

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

data class ApiToken(
    val hash: String = "",
    val name: String = "",
    val scopes: List<String> = emptyList(),
    val created: Long = 0,
    /** Epoch seconds of the last use, or 0 if never used. Server field is "used". */
    @SerializedName("used") val lastUsed: Long = 0,
    /** Epoch seconds of expiry, or 0 if the token never expires. */
    val expires: Long = 0,
)

data class TokensResponse(val tokens: List<ApiToken> = emptyList())

// token/create wraps its payload in `{data: {token, name}}` (the settings app
// envelope); the plaintext token is one level down.
data class TokenCreated(val token: String = "", val name: String = "")
data class TokenCreateResponse(val data: TokenCreated = TokenCreated())

interface TokensApi {
    @GET("settings/-/user/account/tokens")
    suspend fun listTokens(): Response<TokensResponse>

    @FormUrlEncoded
    @POST("settings/-/user/account/token/create")
    suspend fun createToken(
        @Field("token") token: String,
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
object TokensApiModule {
    @Provides
    @Singleton
    fun provideTokensApi(@SettingsRetrofit retrofit: Retrofit): TokensApi =
        retrofit.create(TokensApi::class.java)
}
