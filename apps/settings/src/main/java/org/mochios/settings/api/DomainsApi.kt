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
import retrofit2.http.Query
import javax.inject.Singleton

// Mirrors apps/settings/web/src/hooks/use-domains.ts.
// The web app calls `/settings/-/domains/...` via apiClient;
// Android hits the same endpoints through the settings-app retrofit.

data class Domain(
    val domain: String = "",
    val verified: Int = 0,
    val token: String = "",
    val tls: Int = 0,
    val created: Long = 0,
    val updated: Long = 0,
)

data class Route(
    val domain: String = "",
    val path: String = "",
    val method: String = "",
    val target: String = "",
    val context: String = "",
    val priority: Int = 0,
    val enabled: Int = 1,
    @SerializedName("target_name") val targetName: String? = null,
)

data class Delegation(
    val id: Long = 0,
    val domain: String = "",
    val path: String = "",
    // String UID, not an integer. The server's domains layer
    // (mochi.domain.delegation.*) takes and stores owner as a string user UID;
    // the delegation list enriches each row with the resolved username.
    val owner: String = "",
    val username: String = "",
)

data class DomainsListData(
    @SerializedName("domains") val domains: List<Domain> = emptyList(),
    @SerializedName("delegations") val delegations: List<Delegation>? = null,
    @SerializedName("count") val count: Int = 0,
    @SerializedName("admin") val admin: Boolean = false,
)

data class DomainDetailsData(
    @SerializedName("domain") val domain: Domain = Domain(),
    @SerializedName("routes") val routes: List<Route> = emptyList(),
    @SerializedName("delegations") val delegations: List<Delegation> = emptyList(),
    @SerializedName("admin") val admin: Boolean = false,
)

data class VerifyResponse(
    @SerializedName("verified") val verified: Boolean = false,
)

/** A user match for the delegation autocomplete (admin only). The identifier is
 *  the string UID (mochi.user.search selects `uid`), used as the delegation
 *  owner. */
data class UserSearchResult(
    @SerializedName("uid") val uid: String = "",
    @SerializedName("username") val username: String = "",
    @SerializedName("role") val role: String = "",
)

data class UserSearchResponse(
    @SerializedName("users") val users: List<UserSearchResult> = emptyList(),
)

/** An installed app available as a route target (method = "app"). */
data class RouteApp(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("latest") val latest: String = "",
)

data class RouteAppsResponse(
    @SerializedName("apps") val apps: List<RouteApp> = emptyList(),
)

/** An entity owned by the current user, available as a route target
 *  (method = "entity"). */
data class RouteEntity(
    @SerializedName("id") val id: String = "",
    @SerializedName("fingerprint") val fingerprint: String = "",
    @SerializedName("class") val className: String = "",
    @SerializedName("name") val name: String = "",
)

data class RouteEntitiesResponse(
    @SerializedName("entities") val entities: List<RouteEntity> = emptyList(),
)

interface DomainsApi {
    @GET("settings/-/domains/data")
    suspend fun getDomains(): Response<DomainsListData>

    @GET("settings/-/domains/get")
    suspend fun getDomain(@Query("domain") domain: String): Response<DomainDetailsData>

    @FormUrlEncoded
    @POST("settings/-/domains/create")
    suspend fun createDomain(@Field("domain") domain: String): Response<Unit>

    @FormUrlEncoded
    @POST("settings/-/domains/delete")
    suspend fun deleteDomain(@Field("domain") domain: String): Response<Unit>

    @FormUrlEncoded
    @POST("settings/-/domains/verify")
    suspend fun verifyDomain(@Field("domain") domain: String): Response<VerifyResponse>

    @FormUrlEncoded
    @POST("settings/-/domains/update")
    suspend fun updateDomain(
        @Field("domain") domain: String,
        @Field("verified") verified: String? = null,
        @Field("tls") tls: String? = null,
    ): Response<Unit>

    @FormUrlEncoded
    @POST("settings/-/domains/route/create")
    suspend fun createRoute(
        @Field("domain") domain: String,
        @Field("path") path: String,
        @Field("method") method: String,
        @Field("target") target: String,
        @Field("priority") priority: Int,
    ): Response<Unit>

    @FormUrlEncoded
    @POST("settings/-/domains/route/update")
    suspend fun updateRoute(
        @Field("domain") domain: String,
        @Field("path") path: String,
        @Field("method") method: String? = null,
        @Field("target") target: String? = null,
        @Field("priority") priority: Int? = null,
        @Field("enabled") enabled: String? = null,
    ): Response<Unit>

    @FormUrlEncoded
    @POST("settings/-/domains/route/delete")
    suspend fun deleteRoute(
        @Field("domain") domain: String,
        @Field("path") path: String,
    ): Response<Unit>

    @FormUrlEncoded
    @POST("settings/-/domains/delegation/create")
    suspend fun createDelegation(
        @Field("domain") domain: String,
        @Field("path") path: String,
        @Field("owner") owner: String,
    ): Response<Unit>

    @FormUrlEncoded
    @POST("settings/-/domains/delegation/delete")
    suspend fun deleteDelegation(
        @Field("domain") domain: String,
        @Field("path") path: String,
        @Field("owner") owner: String,
    ): Response<Unit>

    /** Search users by username for the delegation autocomplete (admin only). */
    @GET("settings/-/domains/user/search")
    suspend fun searchUsers(@Query("query") query: String): Response<UserSearchResponse>

    /** List installed apps available as route targets. */
    @GET("settings/-/domains/apps")
    suspend fun listApps(): Response<RouteAppsResponse>

    /** List entities owned by the current user, available as route targets. */
    @GET("settings/-/domains/entities")
    suspend fun listEntities(): Response<RouteEntitiesResponse>
}

@Module
@InstallIn(SingletonComponent::class)
object DomainsApiModule {
    @Provides
    @Singleton
    fun provideDomainsApi(@SettingsRetrofit retrofit: Retrofit): DomainsApi =
        retrofit.create(DomainsApi::class.java)
}
