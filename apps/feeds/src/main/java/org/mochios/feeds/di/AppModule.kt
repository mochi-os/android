// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.mochios.android.auth.SessionManager
import org.mochios.feeds.api.FeedsApi
import org.mochios.feeds.api.MenuApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FeedsRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MenuRetrofit

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @FeedsRetrofit
    fun provideFeedsRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson,
        sessionManager: SessionManager
    ): Retrofit {
        val serverUrl = sessionManager.getServerUrlBlocking().trimEnd('/')
        val feedsClient = okHttpClient.newBuilder()
            .addInterceptor(Interceptor { chain ->
                val token = sessionManager.getTokenBlocking("feeds")
                val builder = chain.request().newBuilder()
                if (token != null) {
                    builder.header("Authorization", "Bearer $token")
                }
                chain.proceed(builder.build())
            })
            .build()
        return Retrofit.Builder()
            .baseUrl("$serverUrl/feeds/")
            .client(feedsClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideFeedsApi(@FeedsRetrofit retrofit: Retrofit): FeedsApi {
        return retrofit.create(FeedsApi::class.java)
    }

    @Provides
    @Singleton
    @MenuRetrofit
    fun provideMenuRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson,
        sessionManager: SessionManager
    ): Retrofit {
        // The shell's menu service, used for the permission-request flow. Its
        // "menu" token is minted in the background at app bootstrap.
        val serverUrl = sessionManager.getServerUrlBlocking().trimEnd('/')
        val menuClient = okHttpClient.newBuilder()
            .addInterceptor(Interceptor { chain ->
                val token = sessionManager.getTokenBlocking("menu")
                val builder = chain.request().newBuilder()
                if (token != null) {
                    builder.header("Authorization", "Bearer $token")
                }
                chain.proceed(builder.build())
            })
            .build()
        return Retrofit.Builder()
            .baseUrl("$serverUrl/menu/")
            .client(menuClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideMenuApi(@MenuRetrofit retrofit: Retrofit): MenuApi {
        return retrofit.create(MenuApi::class.java)
    }
}
