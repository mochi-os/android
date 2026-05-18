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
    val owner: Long = 0,
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
        @Field("owner") owner: Long,
    ): Response<Unit>

    @FormUrlEncoded
    @POST("settings/-/domains/delegation/delete")
    suspend fun deleteDelegation(
        @Field("domain") domain: String,
        @Field("path") path: String,
        @Field("owner") owner: Long,
    ): Response<Unit>
}

@Module
@InstallIn(SingletonComponent::class)
object DomainsApiModule {
    @Provides
    @Singleton
    fun provideDomainsApi(@SettingsRetrofit retrofit: Retrofit): DomainsApi =
        retrofit.create(DomainsApi::class.java)
}
