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
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import javax.inject.Singleton

// Mirrors apps/settings/web/src/types/networks.ts: the Networks page's handles
// and accounts on other networks, and the owner's view of them (the federation
// plan, phases 9 to 11; Android in phase 31).

/** A hosted or linked account on another network, as mochi.account.list answers it. */
data class NetworkAccount(
    val id: String = "",
    val type: String = "",
    val label: String = "",
    val identifier: String = "",
    val created: Long = 0,
    val hosted: Boolean = false,
    // "group" for a forum's community account.
    val kind: String = "",
    val server: String = "",
    val domain: String = "",
    val url: String = "",
    val name: String = "",
    val connected: Boolean = false,
    // Why a linked account is not working, which the card words. A server too
    // old to answer it gives only error, the text itself.
    val failure: NetworkFailure? = null,
    val error: String = "",
    // Shown on the user's profile page, with a rel="me" link.
    val profile: Boolean = false,
    // A hosted account fed by sources rather than the person.
    val automated: Boolean = false,
    // A hosted fediverse account's other identities, and where it has moved.
    val aliases: List<String> = emptyList(),
    val moved: String = "",
    // A hosted Bluesky account's identifier, whether a recovery key is kept
    // for it, and the labellers it reads beside the server's.
    val did: String = "",
    val recovery: Boolean = false,
    val labelers: List<String> = emptyList(),
    // A hosted fediverse or Bluesky account's home feed.
    val home: String = "",
)

/**
 * Why a linked account is not working: a reason, the status its server
 * answered with, and when it tries again, in seconds.
 */
data class NetworkFailure(
    val reason: String = "",
    val status: Int = 0,
    val retry: Long = 0,
)

/** One of the user's feeds, as a home feed is chosen from them. */
data class NetworkFeed(
    val id: String = "",
    val name: String = "",
    val privacy: String = "",
)

/** A handle, or a name under one, with the address it has on each network. */
data class Handle(
    val domain: String = "",
    val name: String = "",
    val parent: String = "",
    val kind: String = "",
    val created: Long = 0,
    val addresses: Map<String, String> = emptyMap(),
)

data class Handles(
    val handles: List<Handle> = emptyList(),
    val limit: Int = 0,
    val counted: Int = 0,
    val unlimited: Boolean = false,
)

/** How an existing account is linked: a form of these fields, or the remote server's consent. */
data class NetworkLink(
    val flow: String = "form",
    val fields: List<ProviderField> = emptyList(),
)

data class NetworkProvider(
    val type: String = "",
    val capabilities: List<String> = emptyList(),
    val link: NetworkLink = NetworkLink(),
)

/** A domain or a person blocked on other networks. */
data class NetworkBlock(
    val domain: String = "",
    val actor: String = "",
    val created: Long = 0,
)

data class NetworkMute(
    val actor: String = "",
    val created: Long = 0,
)

data class NetworksData(
    val offered: Boolean = false,
    val networks: List<String> = emptyList(),
    val domains: List<String> = emptyList(),
    val handles: Handles = Handles(),
    val accounts: List<NetworkAccount> = emptyList(),
    val providers: List<NetworkProvider> = emptyList(),
    val name: String = "",
    val blocks: List<NetworkBlock> = emptyList(),
    val mutes: List<NetworkMute> = emptyList(),
    val feeds: List<NetworkFeed> = emptyList(),
)

data class Availability(
    val available: Boolean = false,
    val reason: String = "",
    val domain: String = "",
    val name: String = "",
)

/** What a link answers: the account a form linked, or the consent an OAuth link visits. */
data class LinkAnswer(
    val id: String = "",
    val url: String = "",
    val nonce: String = "",
)

data class AccountKey(
    val kind: String = "",
    val algorithm: String = "",
    val created: Long = 0,
)

/** A Bluesky recovery key; the private half is shown once and kept nowhere. */
data class RecoveryKey(
    @SerializedName("private") val secret: String = "",
)

data class MintedPassword(val password: String = "")

// The owner's view: each network's state, and every handle with its holder.

data class Usage(val used: Int = 0, val limit: Int = 0)

data class Certificates(val issued: Int = 0, val limit: Int = 0)

data class Budget(val hour: Usage = Usage(), val day: Usage = Usage())

/** The server's standing with the Bluesky relay. */
data class RelayStanding(
    val host: String = "",
    val status: String = "",
    val accounts: Int = 0,
    val limit: Int = 0,
)

/** A Matrix domain's signing key, by its id. */
data class ServerKey(val domain: String = "", val key: String = "")

/** An XMPP domain's room service. */
data class RoomService(val host: String = "", val state: String = "", val reason: String = "")

data class NetworkState(
    val state: String = "",
    val reason: String = "",
    val certificates: Certificates? = null,
    val budget: Budget? = null,
    val relay: RelayStanding? = null,
    val keys: List<ServerKey> = emptyList(),
    val rooms: List<RoomService> = emptyList(),
)

data class SystemNetworks(
    val development: Boolean = false,
    val networks: Map<String, NetworkState> = emptyMap(),
)

/** An account on a handle, as the registry lists it. */
data class RegistryAccount(
    val id: String = "",
    val network: String = "",
    val identifier: String = "",
    val did: String = "",
    val status: String = "",
)

data class RegistryHandle(
    val domain: String = "",
    val name: String = "",
    val parent: String = "",
    val kind: String = "",
    val created: Long = 0,
    val user: String = "",
    val username: String = "",
    val holder: String = "",
    val accounts: List<RegistryAccount> = emptyList(),
)

/** A page of the registry a search matches, how many it matches, and the server's domains. */
data class SystemHandles(
    val handles: List<RegistryHandle> = emptyList(),
    val count: Int = 0,
    val domains: List<String> = emptyList(),
)

interface NetworksApi {
    @GET("settings/-/user/networks/data")
    suspend fun data(): Response<NetworksData>

    @FormUrlEncoded
    @POST("settings/-/user/networks/handle/available")
    suspend fun available(
        @Field("name") name: String,
        @Field("domain") domain: String,
    ): Response<Availability>

    @FormUrlEncoded
    @POST("settings/-/user/networks/handle/reserve")
    suspend fun reserve(
        @Field("name") name: String,
        @Field("domain") domain: String,
    ): Response<Unit>

    @FormUrlEncoded
    @POST("settings/-/user/networks/handle/release")
    suspend fun release(
        @Field("name") name: String,
        @Field("domain") domain: String,
    ): Response<Unit>

    @FormUrlEncoded
    @POST("settings/-/user/networks/account/create")
    suspend fun create(
        @Field("network") network: String,
        @Field("handle") handle: String,
        @Field("domain") domain: String,
        @Field("arriving") arriving: String? = null,
    ): Response<NetworkAccount>

    /** The provider's fields with `type`, and for a native app's OAuth link `scheme` and `challenge`. */
    @FormUrlEncoded
    @POST("settings/-/user/networks/account/link")
    suspend fun link(@FieldMap fields: Map<String, String>): Response<LinkAnswer>

    @FormUrlEncoded
    @POST("settings/-/user/networks/account/remove")
    suspend fun remove(@Field("id") id: String): Response<Unit>

    @FormUrlEncoded
    @POST("settings/-/user/networks/account/update")
    suspend fun update(
        @Field("id") id: String,
        @Field("label") label: String? = null,
        @Field("profile") profile: Boolean? = null,
        @Field("automated") automated: Boolean? = null,
        @Field("labelers") labelers: String? = null,
    ): Response<NetworkAccount>

    /** The aliases as one JSON list; an empty list clears them. */
    @FormUrlEncoded
    @POST("settings/-/user/networks/account/aliases")
    suspend fun aliases(
        @Field("id") id: String,
        @Field("aliases") aliases: String,
    ): Response<NetworkAccount>

    @FormUrlEncoded
    @POST("settings/-/user/networks/account/move")
    suspend fun move(
        @Field("id") id: String,
        @Field("address") address: String,
    ): Response<NetworkAccount>

    @FormUrlEncoded
    @POST("settings/-/user/networks/account/home")
    suspend fun home(
        @Field("id") id: String,
        @Field("feed") feed: String,
        @Field("create") create: String? = null,
    ): Response<NetworkAccount>

    @FormUrlEncoded
    @POST("settings/-/user/networks/account/test")
    suspend fun test(@Field("id") id: String): Response<AccountTestResult>

    @FormUrlEncoded
    @POST("settings/-/user/networks/account/keys")
    suspend fun keys(@Field("id") id: String): Response<List<AccountKey>>

    @FormUrlEncoded
    @POST("settings/-/user/networks/account/keys/replace")
    suspend fun replaceKeys(@Field("id") id: String): Response<Unit>

    @FormUrlEncoded
    @POST("settings/-/user/networks/account/keys/recovery")
    suspend fun recovery(@Field("id") id: String): Response<RecoveryKey>

    @FormUrlEncoded
    @POST("settings/-/user/networks/account/password")
    suspend fun password(
        @Field("id") id: String,
        @Field("name") name: String,
    ): Response<MintedPassword>

    @FormUrlEncoded
    @POST("settings/-/user/networks/block/add")
    suspend fun block(@Field("target") target: String): Response<Unit>

    @FormUrlEncoded
    @POST("settings/-/user/networks/block/remove")
    suspend fun unblock(@Field("target") target: String): Response<Unit>

    @FormUrlEncoded
    @POST("settings/-/user/networks/mute/remove")
    suspend fun unmute(@Field("actor") actor: String): Response<Unit>

    // The owner's: each network's state, the Matrix server keys, every handle.

    @GET("settings/-/system/networks")
    suspend fun system(): Response<SystemNetworks>

    @FormUrlEncoded
    @POST("settings/-/system/networks/key/replace")
    suspend fun replaceServerKey(
        @Field("domain") domain: String,
        @Field("token") token: String,
    ): Response<Unit>

    @GET("settings/-/system/handles/list")
    suspend fun registry(
        @Query("search") search: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): Response<SystemHandles>

    @FormUrlEncoded
    @POST("settings/-/system/handles/release")
    suspend fun releaseAny(
        @Field("name") name: String,
        @Field("domain") domain: String,
    ): Response<Unit>

    @FormUrlEncoded
    @POST("settings/-/system/accounts/takedown")
    suspend fun takedown(
        @Field("did") did: String,
        @Field("down") down: Boolean,
    ): Response<Unit>
}

@Module
@InstallIn(SingletonComponent::class)
object NetworksApiModule {
    @Provides
    @Singleton
    fun provideNetworksApi(@SettingsRetrofit retrofit: Retrofit): NetworksApi =
        retrofit.create(NetworksApi::class.java)
}
