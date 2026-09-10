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
import org.mochios.settings.api.SettingsRetrofit
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import javax.inject.Singleton

// Mirrors web's use-system-documents.ts. The list carries only the
// (name x language) pairs; get loads one document's body, bundled default
// and last-edit timestamp, and set writes an operator override.

data class SystemDocument(
    val name: String = "",
    val language: String = "",
    val body: String = "",
    val default: String = "",
    val updated: Long = 0,
)

data class SystemDocumentsData(
    @SerializedName("documents") val documents: List<SystemDocument> = emptyList(),
)

interface SystemDocumentsApi {
    @GET("settings/-/system/documents/list")
    suspend fun list(): Response<SystemDocumentsData>

    @GET("settings/-/system/document/get")
    suspend fun get(
        @Query("name") name: String,
        @Query("language") language: String,
    ): Response<SystemDocument>

    @FormUrlEncoded
    @POST("settings/-/system/document/set")
    suspend fun set(
        @Field("name") name: String,
        @Field("language") language: String,
        @Field("body") body: String,
        @Field("token") token: String,
    ): Response<Unit>
}

@Module
@InstallIn(SingletonComponent::class)
object SystemDocumentsApiModule {
    @Provides
    @Singleton
    fun provideSystemDocumentsApi(@SettingsRetrofit retrofit: Retrofit): SystemDocumentsApi =
        retrofit.create(SystemDocumentsApi::class.java)
}
