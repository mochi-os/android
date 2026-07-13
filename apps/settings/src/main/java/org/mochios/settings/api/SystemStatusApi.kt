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

data class PeerEntry(
    val peer: String = "",
    // Announced display name ("" when none) — a self-asserted label. The
    // fingerprint is the authoritative identifier; never feed logic off name.
    val name: String = "",
    val fingerprint: String = "",
    val connected: Boolean = false,
    val unreachable: Boolean = false,
    val address: String = "",
    val seen: Long = 0,
    val addresses: Int = 0,
    val queued: Int = 0,
    val oldest: Long = 0,
)

data class HolePunch(
    val success: Int = 0,
    val failure: Int = 0,
)

data class Reservations(
    val held: Int = 0,
    val maximum: Int = 0,
)

data class Relaying(
    val active: Boolean = false,
    val reservations: Reservations = Reservations(),
    val circuits: Int = 0,
    val rejected: Int = 0,
)

data class NetworkInfo(
    val reachability: String = "",
    val relay: Boolean = false,
    val mesh: Int = 0,
    val last: Long = 0,
    val queued: Int = 0,
    val unresolved: Int = 0,
    val holepunch: HolePunch = HolePunch(),
    val relaying: Relaying = Relaying(),
)

data class ServerCounts(
    val users: Int = 0,
    val entities: Int = 0,
)

data class SystemPeersData(
    val peers: List<PeerEntry> = emptyList(),
    val network: NetworkInfo? = null,
    val counts: ServerCounts? = null,
)

interface SystemStatusApi {
    @GET("settings/-/system/settings/list")
    suspend fun listSettings(): Response<SystemSettingsData>

    @GET("settings/-/system/update")
    suspend fun getUpdate(): Response<SystemUpdateInfo>

    @GET("settings/-/system/peers")
    suspend fun getPeers(): Response<SystemPeersData>

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
