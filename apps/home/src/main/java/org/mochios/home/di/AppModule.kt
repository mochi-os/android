// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.home.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.mochios.android.auth.SessionManager
import org.mochios.home.api.HomeApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

/** The name the client keeps the home app's token under. */
const val APP = "home"

/**
 * Qualifier for the Retrofit bound to the server's root, where the home app
 * is served, with the home app's JWT, so it coexists with other apps'
 * Retrofits.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class HomeRetrofit

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @HomeRetrofit
    fun provideHomeRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson,
        sessionManager: SessionManager,
    ): Retrofit {
        val serverUrl = sessionManager.getServerUrlBlocking().trimEnd('/')
        val client = okHttpClient.newBuilder()
            .addInterceptor(Interceptor { chain ->
                val token = sessionManager.getTokenBlocking(APP)
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
        return Retrofit.Builder()
            .baseUrl("$serverUrl/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideHomeApi(@HomeRetrofit retrofit: Retrofit): HomeApi =
        retrofit.create(HomeApi::class.java)
}
