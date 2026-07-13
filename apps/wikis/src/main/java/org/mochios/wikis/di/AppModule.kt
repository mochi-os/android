// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.mochios.android.auth.SessionManager
import org.mochios.wikis.api.WikisApi
import org.mochios.wikis.model.PageFetchResponse
import org.mochios.wikis.model.PageFetchResponseDeserializer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier for the wikis-app Retrofit (`<server>/wikis/`). Mirrors the
 * per-app pattern used by feeds/forums/projects/people/etc.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WikisRetrofit

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @WikisRetrofit
    fun provideWikisRetrofit(
        okHttpClient: OkHttpClient,
        sessionManager: SessionManager,
    ): Retrofit {
        val serverUrl = sessionManager.getServerUrlBlocking().trimEnd('/')
        val client = okHttpClient.newBuilder()
            .addInterceptor(Interceptor { chain ->
                val token = sessionManager.getTokenBlocking("wikis")
                val request = if (token != null) {
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            })
            .build()
        val gson: Gson = GsonBuilder()
            .registerTypeAdapter(PageFetchResponse::class.java, PageFetchResponseDeserializer())
            .create()
        return Retrofit.Builder()
            .baseUrl("$serverUrl/wikis/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideWikisApi(@WikisRetrofit retrofit: Retrofit): WikisApi =
        retrofit.create(WikisApi::class.java)
}
