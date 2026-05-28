package org.mochios.settings.api

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.mochios.android.auth.SessionManager
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SettingsRetrofit

@Module
@InstallIn(SingletonComponent::class)
object SettingsRetrofitModule {
    @Provides
    @Singleton
    @SettingsRetrofit
    fun provideSettingsRetrofit(
        okHttpClient: OkHttpClient,
        gson: com.google.gson.Gson,
        sessionManager: SessionManager,
    ): Retrofit {
        val serverUrl = sessionManager.getServerUrlBlocking().trimEnd('/')
        val client = okHttpClient.newBuilder()
            .addInterceptor(Interceptor { chain ->
                val token = sessionManager.getTokenBlocking("settings")
                val req = chain.request().newBuilder()
                    .header("Accept", "application/json")
                if (token != null) req.header("Authorization", "Bearer $token")
                chain.proceed(req.build())
            })
            .build()
        return Retrofit.Builder()
            .baseUrl("$serverUrl/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
}
