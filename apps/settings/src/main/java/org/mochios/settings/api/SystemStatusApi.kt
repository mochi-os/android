package org.mochios.settings.api

import com.google.gson.annotations.SerializedName
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.mochios.settings.api.SettingsRetrofit
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import javax.inject.Singleton

// Mirrors web's use-system-settings.ts + use-system-update.ts. The status
// page reads the full system settings list (filters server_version and
// server_started — types live in SystemSettingsApi) and the update info
// (current, latest, platform, pending). install=true triggers a Windows
// self-install (no-op elsewhere).

data class SystemUpdateInfo(
    val available: Boolean = false,
    val current: String = "",
    val latest: String = "",
    val platform: String = "",
    val track: String = "",
    val checked: Long = 0,
    val pending: String = "",
)

interface SystemStatusApi {
    @GET("settings/-/system/settings/list")
    suspend fun listSettings(): Response<SystemSettingsData>

    @GET("settings/-/system/update")
    suspend fun getUpdate(): Response<SystemUpdateInfo>

    @FormUrlEncoded
    @POST("settings/-/system/update")
    suspend fun installUpdate(@Field("install") install: String = "true"): Response<Unit>
}

@Module
@InstallIn(SingletonComponent::class)
object SystemStatusApiModule {
    @Provides
    @Singleton
    fun provideSystemStatusApi(@SettingsRetrofit retrofit: Retrofit): SystemStatusApi =
        retrofit.create(SystemStatusApi::class.java)
}
