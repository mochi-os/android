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

// Mirrors lib/web's use-system-settings.ts. The web app calls
// `/settings/-/system/settings/{list,set}` via apiClient; Android hits the same
// endpoints through SettingsApi's settings-app retrofit. Admin-only.

data class SystemSetting(
    val name: String = "",
    val value: String = "",
    val default: String = "",
    val description: String = "",
    val pattern: String = "",
    @SerializedName("user_readable") val userReadable: Boolean = false,
    @SerializedName("read_only") val readOnly: Boolean = false,
    val public: Boolean = false,
)

data class SystemSettingsServer(
    val id: String = "",
    val fingerprint: String = "",
)

data class SystemSettingsData(
    val settings: List<SystemSetting> = emptyList(),
    val server: SystemSettingsServer = SystemSettingsServer(),
)

interface SystemSettingsApi {
    @GET("settings/-/system/settings/list")
    suspend fun list(): Response<SystemSettingsData>

    @FormUrlEncoded
    @POST("settings/-/system/settings/set")
    suspend fun set(
        @Field("name") name: String,
        @Field("value") value: String,
    ): Response<Unit>
}

@Module
@InstallIn(SingletonComponent::class)
object SystemSettingsApiModule {
    @Provides
    @Singleton
    fun provideSystemSettingsApi(@SettingsRetrofit retrofit: Retrofit): SystemSettingsApi =
        retrofit.create(SystemSettingsApi::class.java)
}
