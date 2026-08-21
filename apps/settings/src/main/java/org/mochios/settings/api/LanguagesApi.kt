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
import retrofit2.http.GET
import javax.inject.Singleton

data class LanguagesResponse(val languages: List<String> = emptyList())

/**
 * Language tags the server has catalogues for. Always fetched, never hardcoded:
 * the set grows as locales are added. Public endpoint.
 */
interface LanguagesApi {
    @GET("_/languages")
    suspend fun list(): Response<LanguagesResponse>
}

@Module
@InstallIn(SingletonComponent::class)
object LanguagesApiModule {
    @Provides
    @Singleton
    fun provideLanguagesApi(@SettingsRetrofit retrofit: Retrofit): LanguagesApi =
        retrofit.create(LanguagesApi::class.java)
}
