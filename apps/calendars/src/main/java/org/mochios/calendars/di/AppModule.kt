// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.mochios.android.auth.SessionManager
import org.mochios.android.i18n.PreferencesManager
import org.mochios.calendars.api.CalendarsApi
import org.mochios.calendars.api.MenuApi
import retrofit2.Retrofit
import kotlinx.coroutines.flow.first
import retrofit2.converter.gson.GsonConverterFactory
import java.time.ZoneId
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CalendarsRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MenuRetrofit

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @CalendarsRetrofit
    fun provideCalendarsRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson,
        sessionManager: SessionManager,
    ): Retrofit {
        val serverUrl = sessionManager.getServerUrlBlocking().trimEnd('/')
        val client = okHttpClient.newBuilder()
            .addInterceptor(Interceptor { chain ->
                val token = sessionManager.getTokenBlocking("calendars")
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
            .baseUrl("$serverUrl/calendars/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideCalendarsApi(@CalendarsRetrofit retrofit: Retrofit): CalendarsApi =
        retrofit.create(CalendarsApi::class.java)

    @Provides
    @Singleton
    @MenuRetrofit
    fun provideMenuRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson,
        sessionManager: SessionManager,
    ): Retrofit {
        // The shell's menu service, used for the permission-request flow when
        // subscribing to an external calendar. Its "menu" token is minted in
        // the background at app bootstrap.
        val serverUrl = sessionManager.getServerUrlBlocking().trimEnd('/')
        val client = okHttpClient.newBuilder()
            .addInterceptor(Interceptor { chain ->
                val token = sessionManager.getTokenBlocking("menu")
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
            .baseUrl("$serverUrl/menu/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideMenuApi(@MenuRetrofit retrofit: Retrofit): MenuApi =
        retrofit.create(MenuApi::class.java)

    @Provides
    fun provideViewer(preferences: PreferencesManager, session: SessionManager): Viewer = object : Viewer {
        override fun zone(): String =
            runCatching { ZoneId.of(preferences.preferences.value.timezone).id }.getOrDefault(ZoneId.systemDefault().id)

        override fun week(): Int = preferences.preferences.value.weekStartsOn

        override suspend fun server(): String = session.serverUrl.first()
    }
}

/**
 * The person looking at the calendar, as the screens need them: the zone
 * every range and new event is measured in, their own where their
 * preferences name one the platform knows; the first day of their week, 0
 * Sunday to 6 Saturday; and the server they are signed in to.
 */
interface Viewer {
    fun zone(): String

    fun week(): Int

    suspend fun server(): String
}
