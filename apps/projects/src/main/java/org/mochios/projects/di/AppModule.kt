package org.mochios.projects.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.mochios.android.auth.SessionManager
import org.mochios.projects.api.ProjectsApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideProjectsApi(
        okHttpClient: OkHttpClient,
        gson: Gson,
        sessionManager: SessionManager
    ): ProjectsApi {
        val serverUrl = sessionManager.getServerUrlBlocking().trimEnd('/')
        val projectsClient = okHttpClient.newBuilder()
            .addInterceptor(Interceptor { chain ->
                val token = sessionManager.getTokenBlocking("projects")
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
            .baseUrl("$serverUrl/projects/")
            .client(projectsClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ProjectsApi::class.java)
    }
}
