// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.api

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.mochios.settings.api.SettingsRetrofit
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import javax.inject.Singleton

// Mirrors apps/settings/web/src/features/user/connected-accounts.tsx. The web app
// calls the settings app's `-/accounts/*` endpoints (which proxy to mochi.account.*).

data class ConnectedAccount(
    val id: String = "",
    val type: String = "",
    val label: String = "",
    val identifier: String = "",
    val created: Long = 0,
    val verified: Int = 0,
    val enabled: Int = 0,
    val default: String = "",
)

data class ProviderField(
    val name: String = "",
    val label: String = "",
    val type: String = "text",
    val required: Boolean = false,
    val placeholder: String = "",
)

data class Provider(
    val type: String = "",
    val capabilities: List<String> = emptyList(),
    val flow: String = "form",
    val fields: List<ProviderField> = emptyList(),
    val verify: Boolean = false,
)

data class AccountTestResult(
    val success: Boolean = false,
    val message: String = "",
)

interface ConnectedAccountsApi {
    @GET("settings/-/accounts/providers")
    suspend fun providers(): Response<List<Provider>>

    @GET("settings/-/accounts/list")
    suspend fun list(): Response<List<ConnectedAccount>>

    @FormUrlEncoded
    @POST("settings/-/accounts/add")
    suspend fun add(@FieldMap fields: Map<String, String>): Response<ConnectedAccount>

    @FormUrlEncoded
    @POST("settings/-/accounts/update")
    suspend fun update(@FieldMap fields: Map<String, String>): Response<Unit>

    @FormUrlEncoded
    @POST("settings/-/accounts/remove")
    suspend fun remove(@Field("id") id: String): Response<Unit>

    @FormUrlEncoded
    @POST("settings/-/accounts/verify")
    suspend fun verify(
        @Field("id") id: String,
        @Field("code") code: String? = null,
    ): Response<Map<String, Any?>>

    @FormUrlEncoded
    @POST("settings/-/accounts/test")
    suspend fun test(@Field("id") id: String): Response<AccountTestResult>

    @FormUrlEncoded
    @POST("settings/-/accounts/default")
    suspend fun setDefault(
        @Field("account") account: String,
        @Field("type") type: String,
    ): Response<Unit>
}

@Module
@InstallIn(SingletonComponent::class)
object ConnectedAccountsApiModule {
    @Provides
    @Singleton
    fun provideConnectedAccountsApi(@SettingsRetrofit retrofit: Retrofit): ConnectedAccountsApi =
        retrofit.create(ConnectedAccountsApi::class.java)
}
