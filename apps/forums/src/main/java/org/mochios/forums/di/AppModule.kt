package org.mochios.forums.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.mochios.android.auth.SessionManager
import org.mochios.forums.api.ForumsApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideForumsApi(
        okHttpClient: OkHttpClient,
        gson: Gson,
        sessionManager: SessionManager
    ): ForumsApi {
        val serverUrl = sessionManager.getServerUrlBlocking().trimEnd('/')
        val forumsClient = okHttpClient.newBuilder()
            .addInterceptor(Interceptor { chain ->
                val token = sessionManager.getTokenBlocking("forums")
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
            .baseUrl("$serverUrl/forums/")
            .client(forumsClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ForumsApi::class.java)
    }
}
