// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.mochios.android.auth.SessionManager
import org.mochios.crm.api.CrmsApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideCrmsApi(
        okHttpClient: OkHttpClient,
        gson: Gson,
        sessionManager: SessionManager
    ): CrmsApi {
        val serverUrl = sessionManager.getServerUrlBlocking().trimEnd('/')
        val crmsClient = okHttpClient.newBuilder()
            .addInterceptor(Interceptor { chain ->
                val token = sessionManager.getTokenBlocking("crm")
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
            .baseUrl("$serverUrl/crm/")
            .client(crmsClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(CrmsApi::class.java)
    }
}
