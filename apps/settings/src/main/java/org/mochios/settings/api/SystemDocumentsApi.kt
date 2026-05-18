package org.mochios.settings.api

import com.google.gson.annotations.SerializedName
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.mochios.settings.ui.profile.SettingsRetrofit
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import javax.inject.Singleton

// Mirrors web's use-system-documents.ts. The admin screen lists every
// (name x language) row with body + bundled default + last-edit timestamp,
// and writes operator overrides through documentSet.

data class SystemDocument(
    val name: String = "",
    val language: String = "",
    val body: String = "",
    val default: String = "",
    val updated: Long = 0,
)

data class SystemDocumentsData(
    @SerializedName("documents") val documents: List<SystemDocument> = emptyList(),
)

interface SystemDocumentsApi {
    @GET("settings/-/system/documents/list")
    suspend fun list(): Response<SystemDocumentsData>

    @FormUrlEncoded
    @POST("settings/-/system/document/set")
    suspend fun set(
        @Field("name") name: String,
        @Field("language") language: String,
        @Field("body") body: String,
    ): Response<Unit>
}

@Module
@InstallIn(SingletonComponent::class)
object SystemDocumentsApiModule {
    @Provides
    @Singleton
    fun provideSystemDocumentsApi(@SettingsRetrofit retrofit: Retrofit): SystemDocumentsApi =
        retrofit.create(SystemDocumentsApi::class.java)
}
