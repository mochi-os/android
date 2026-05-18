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

// Mirrors lib/web's use-replication.ts. The web app calls
// `/settings/-/user/replication/{data,approve,deny,remove}` via apiClient;
// Android hits the same endpoints through SettingsApi's settings-app retrofit.

data class ReplicationLink(
    val peer: String = "",
    val label: String = "",
    val expires: Long = 0,
)

data class ReplicationHost(
    val peer: String = "",
    val added: Long = 0,
    val ack: Long = 0,
)

data class ReplicationData(
    @SerializedName("links") val links: List<ReplicationLink> = emptyList(),
    @SerializedName("hosts") val hosts: List<ReplicationHost> = emptyList(),
)

interface ReplicationApi {
    @GET("settings/-/user/replication/data")
    suspend fun getReplication(): Response<ReplicationData>

    @FormUrlEncoded
    @POST("settings/-/user/replication/approve")
    suspend fun approveLink(@Field("peer") peer: String): Response<Unit>

    @FormUrlEncoded
    @POST("settings/-/user/replication/deny")
    suspend fun denyLink(@Field("peer") peer: String): Response<Unit>

    @FormUrlEncoded
    @POST("settings/-/user/replication/remove")
    suspend fun removeHost(@Field("peer") peer: String): Response<Unit>
}

@Module
@InstallIn(SingletonComponent::class)
object ReplicationApiModule {
    @Provides
    @Singleton
    fun provideReplicationApi(@SettingsRetrofit retrofit: Retrofit): ReplicationApi =
        retrofit.create(ReplicationApi::class.java)
}
