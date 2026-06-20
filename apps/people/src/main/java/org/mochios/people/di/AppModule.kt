// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.mochios.android.auth.SessionManager
import org.mochios.people.api.PeopleApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier for the Retrofit instance bound to the people-app HTTP base
 * (`<server>/people/`). Lets the module coexist with other apps' Retrofits
 * (each scoped to its own base URL + per-app JWT) inside a single
 * SingletonComponent without colliding.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PeopleRetrofit

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @PeopleRetrofit
    fun providePeopleRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson,
        sessionManager: SessionManager,
    ): Retrofit {
        val serverUrl = sessionManager.getServerUrlBlocking().trimEnd('/')
        val client = okHttpClient.newBuilder()
            .addInterceptor(Interceptor { chain ->
                val token = sessionManager.getTokenBlocking("people")
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
            .baseUrl("$serverUrl/people/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun providePeopleApi(@PeopleRetrofit retrofit: Retrofit): PeopleApi =
        retrofit.create(PeopleApi::class.java)
}
