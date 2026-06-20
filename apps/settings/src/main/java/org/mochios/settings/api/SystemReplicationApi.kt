// Copyright © 2026 Mochi OÜ
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

// Mirrors lib/web's use-system-replication.ts. Server-admin "pair page" — manages
// server-to-server replication: pending join requests + established pair members.

data class PendingJoin(
    val peer: String = "",
    val label: String = "",
    val expires: Long = 0,
    val name: String = "",
    val fingerprint: String = "",
)

data class PairMember(
    val peer: String = "",
    val name: String = "",
    val fingerprint: String = "",
)

data class BootstrapEntry(
    val peer: String = "",
    val scope: String = "",
    val state: String = "",
    val position: String = "",
)

data class SystemReplicationData(
    @SerializedName("peer") val peer: String = "",
    @SerializedName("fingerprint") val fingerprint: String = "",
    @SerializedName("addresses") val addresses: List<String> = emptyList(),
    @SerializedName("pair") val pair: List<PairMember> = emptyList(),
    @SerializedName("joins") val joins: List<PendingJoin> = emptyList(),
    @SerializedName("bootstrap") val bootstrap: List<BootstrapEntry> = emptyList(),
    @SerializedName("bootstrap_pending") val bootstrapPending: Int = 0,
)

interface SystemReplicationApi {
    @GET("settings/-/system/replication/data")
    suspend fun getSystemReplication(): Response<SystemReplicationData>

    // Approving a join replicates every user's private keys to the peer, so the
    // server gates it on operator step-up re-authentication (the proof token).
    @FormUrlEncoded
    @POST("settings/-/system/replication/join/approve")
    suspend fun approveJoin(
        @Field("peer") peer: String,
        @Field("token") token: String,
    ): Response<Unit>

    @FormUrlEncoded
    @POST("settings/-/system/replication/join/deny")
    suspend fun denyJoin(@Field("peer") peer: String): Response<Unit>

    @FormUrlEncoded
    @POST("settings/-/system/replication/pair/remove")
    suspend fun removePair(@Field("peer") peer: String): Response<Unit>
}

@Module
@InstallIn(SingletonComponent::class)
object SystemReplicationApiModule {
    @Provides
    @Singleton
    fun provideSystemReplicationApi(@SettingsRetrofit retrofit: Retrofit): SystemReplicationApi =
        retrofit.create(SystemReplicationApi::class.java)
}
