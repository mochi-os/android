// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.mochios.android.auth.SessionManager
import org.mochios.staff.access.StaffAuthInterceptor
import org.mochios.staff.api.StaffApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier for the staff-app Retrofit (`<server>/staff/`). Mirrors the
 * per-app pattern used by market/feeds/forums/projects/wikis/people/etc.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StaffRetrofit

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @StaffRetrofit
    fun provideStaffRetrofit(
        okHttpClient: OkHttpClient,
        sessionManager: SessionManager,
    ): Retrofit {
        val serverUrl = sessionManager.getServerUrlBlocking().trimEnd('/')
        // Interceptor order matters here. OkHttp runs application
        // interceptors in declaration order on the request and in reverse
        // on the response. The Bearer interceptor must come FIRST so it
        // attaches the staff JWT on the way out; StaffAuthInterceptor sits
        // after it so the 401/403 it observes is the response to the
        // authenticated request (rather than the unauthenticated one we'd
        // otherwise be sending).
        val client = okHttpClient.newBuilder()
            .addInterceptor(Interceptor { chain ->
                val token = sessionManager.getTokenBlocking("staff")
                val request = if (token != null) {
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            })
            .addInterceptor(StaffAuthInterceptor())
            .build()
        val gson: Gson = GsonBuilder().create()
        return Retrofit.Builder()
            .baseUrl("$serverUrl/staff/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideStaffApi(@StaffRetrofit retrofit: Retrofit): StaffApi =
        retrofit.create(StaffApi::class.java)
}
