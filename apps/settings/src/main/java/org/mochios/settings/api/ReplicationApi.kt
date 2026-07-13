// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

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

// Mirrors lib/web's use-replication.ts. The web app calls
// `/settings/-/user/replication/{data,approve,deny,remove}` via apiClient;
// Android hits the same endpoints through SettingsApi's settings-app retrofit.

data class ReplicationLink(
    val peer: String = "",
    val label: String = "",
    val expires: Long = 0,
    val name: String = "",
    val fingerprint: String = "",
)

data class ReplicationHost(
    val peer: String = "",
    val added: Long = 0,
    val ack: Long = 0,
    // irreparable: the host can never be reached again (operator told us);
    // offline: unix seconds since it was last reachable (0 when reachable).
    val irreparable: Boolean = false,
    val offline: Long = 0,
    val name: String = "",
    val fingerprint: String = "",
)

data class ReplicationServer(
    @SerializedName("id") val id: String = "",
    @SerializedName("fingerprint") val fingerprint: String = "",
)

data class ReplicationUser(
    @SerializedName("username") val username: String = "",
)

data class ReplicationData(
    @SerializedName("links") val links: List<ReplicationLink> = emptyList(),
    @SerializedName("hosts") val hosts: List<ReplicationHost> = emptyList(),
    @SerializedName("server") val server: ReplicationServer = ReplicationServer(),
    @SerializedName("user") val user: ReplicationUser = ReplicationUser(),
)

interface ReplicationApi {
    @GET("settings/-/user/replication/data")
    suspend fun getReplication(): Response<ReplicationData>

    // Approving replicates the user's private keys to the peer, so the server
    // gates it on step-up re-authentication (the proof token).
    @FormUrlEncoded
    @POST("settings/-/user/replication/approve")
    suspend fun approveLink(
        @Field("peer") peer: String,
        @Field("token") token: String,
    ): Response<Unit>

    @FormUrlEncoded
    @POST("settings/-/user/replication/deny")
    suspend fun denyLink(@Field("peer") peer: String): Response<Unit>

    // Remove the account from THIS server (leave the replica set). Step-up
    // gated; the local copy is purged, the account stays on other servers.
    @FormUrlEncoded
    @POST("settings/-/user/replication/leave")
    suspend fun leave(@Field("token") token: String): Response<Unit>

    // Advanced: forget an unreachable host. Step-up gated.
    @FormUrlEncoded
    @POST("settings/-/user/replication/remove")
    suspend fun removeHost(
        @Field("peer") peer: String,
        @Field("token") token: String,
    ): Response<Unit>
}

@Module
@InstallIn(SingletonComponent::class)
object ReplicationApiModule {
    @Provides
    @Singleton
    fun provideReplicationApi(@SettingsRetrofit retrofit: Retrofit): ReplicationApi =
        retrofit.create(ReplicationApi::class.java)
}
