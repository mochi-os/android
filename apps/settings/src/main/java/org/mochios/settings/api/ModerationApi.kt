// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.api

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import javax.inject.Singleton

/**
 * Someone in a report: their address on their network, the name they are
 * shown by, whether they can be blocked, and the other server they are on.
 */
data class ReportParty(
    val id: String = "",
    val name: String = "",
    val blockable: Boolean = false,
    val host: String = "",
)

/** The local user a report is about. */
data class ReportAccount(
    val username: String = "",
    val status: String = "",
)

data class ReportRoom(
    val id: String = "",
    val name: String = "",
)

data class Report(
    val id: String = "",
    val network: String = "",
    val reporter: ReportParty = ReportParty(),
    val person: ReportParty? = null,
    val user: String = "",
    val account: ReportAccount? = null,
    val room: ReportRoom? = null,
    val objects: List<String> = emptyList(),
    val category: String = "",
    val comment: String = "",
    val content: String = "",
    val created: Long = 0,
    val resolved: Long = 0,
    val outcome: String = "",
)

data class SystemReports(
    val reports: List<Report> = emptyList(),
)

data class BlockedDomain(
    val domain: String = "",
    val created: Long = 0,
)

data class BlockedPerson(
    val actor: String = "",
    val created: Long = 0,
)

data class SystemBlocks(
    val domains: List<BlockedDomain> = emptyList(),
    val people: List<BlockedPerson> = emptyList(),
)

data class FediverseHost(
    val host: String = "",
    val failures: Long = 0,
    val success: Long = 0,
    val failure: Long = 0,
    val next: Long = 0,
    val error: String = "",
    val waiting: Long = 0,
    val limited: Boolean = false,
)

data class MatrixHost(
    val server: String = "",
    val failures: Long = 0,
    val next: Long = 0,
    val waiting: Long = 0,
)

data class XmppLink(
    val server: String = "",
    val domain: String = "",
    val up: Boolean = false,
    val failures: Long = 0,
    val next: Long = 0,
    val waiting: Long = 0,
)

data class XmppSessions(
    val clients: Long = 0,
    val accounts: Long = 0,
)

data class XmppHealth(
    val links: List<XmppLink> = emptyList(),
    val sessions: XmppSessions = XmppSessions(),
)

data class SystemHosts(
    val fediverse: List<FediverseHost> = emptyList(),
    val matrix: List<MatrixHost> = emptyList(),
    val xmpp: XmppHealth = XmppHealth(),
)

/** The administrators' reports, the owner's blocks and the servers this one talks to. */
interface ModerationApi {
    @GET("settings/-/system/reports/list")
    suspend fun reports(@Query("state") state: String): Response<SystemReports>

    @FormUrlEncoded
    @POST("settings/-/system/reports/resolve")
    suspend fun resolve(
        @Field("id") id: String,
        @Field("outcome") outcome: String,
        // The person or server blocked, for an outcome of blocked.
        @Field("target") target: String? = null,
        // The step-up proof a suspension takes.
        @Field("token") token: String? = null,
    ): Response<Unit>

    @GET("settings/-/system/blocks/list")
    suspend fun blocks(): Response<SystemBlocks>

    @FormUrlEncoded
    @POST("settings/-/system/blocks/add")
    suspend fun block(@Field("target") target: String): Response<SystemBlocks>

    @FormUrlEncoded
    @POST("settings/-/system/blocks/remove")
    suspend fun unblock(@Field("target") target: String): Response<SystemBlocks>

    @GET("settings/-/system/hosts")
    suspend fun hosts(): Response<SystemHosts>
}

@Module
@InstallIn(SingletonComponent::class)
object ModerationApiModule {
    @Provides
    @Singleton
    fun provideModerationApi(@SettingsRetrofit retrofit: Retrofit): ModerationApi =
        retrofit.create(ModerationApi::class.java)
}
