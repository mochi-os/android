// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.api

import okhttp3.MultipartBody
import org.mochios.android.api.ApiResponse
import org.mochios.people.model.Friend
import org.mochios.people.model.FriendInvite
import org.mochios.people.model.Group
import org.mochios.people.model.GroupMember
import org.mochios.people.model.LocalUser
import org.mochios.people.model.PersonInformation
import org.mochios.people.model.PersonStyle
import org.mochios.people.model.User
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

// ---------------------------------------------------------------------------
// Response wrappers
//
// The people app's HTTP actions wrap their payloads in `{"data": ...}` (the
// standard Mochi app envelope). The library's ApiResponse<T> already unwraps
// that one level, so each wrapper here mirrors the *inner* shape returned
// by the corresponding action.

/**
 * Result of `/-/friends`. Friends, received invites and sent invites all use
 * the same `Friend` record shape — direction is implied by which collection
 * the row appears in. Total / page / limit are optional pagination hints the
 * server may or may not emit depending on size.
 */
data class FriendsListResponse(
    val friends: List<Friend> = emptyList(),
    val received: List<FriendInvite> = emptyList(),
    val sent: List<FriendInvite> = emptyList(),
    val total: Int? = null,
    val page: Int? = null,
    val limit: Int? = null,
)

data class SearchUsersResponse(val results: List<User> = emptyList())

data class SearchLocalUsersResponse(val results: List<LocalUser> = emptyList())

data class GetGroupsResponse(val groups: List<Group> = emptyList())

data class GetGroupResponse(
    val group: Group = Group(),
    val members: List<GroupMember> = emptyList(),
)

data class CreateGroupResponse(val id: String = "")

data class WelcomeResponse(
    val seen: Boolean = false,
    val count: Int = 0,
)

data class PreferenceResponse(
    @com.google.gson.annotations.SerializedName("invite_policy")
    val invitePolicy: String = "notify"
)

data class IdResponse(val id: String = "")

data class EmptyResponse(val ok: Boolean = true)

interface PeopleApi {

    // ---- Friends ----

    @GET("-/friends")
    suspend fun listFriends(): Response<ApiResponse<FriendsListResponse>>

    @FormUrlEncoded
    @POST("-/friends/search")
    suspend fun searchFriends(@Field("search") query: String): Response<ApiResponse<SearchUsersResponse>>

    @FormUrlEncoded
    @POST("-/friends/create")
    suspend fun createFriend(
        @Field("id") id: String,
        @Field("name") name: String,
    ): Response<ApiResponse<EmptyResponse>>

    @FormUrlEncoded
    @POST("-/friends/accept")
    suspend fun acceptInvite(@Field("id") id: String): Response<ApiResponse<EmptyResponse>>

    @FormUrlEncoded
    @POST("-/friends/ignore")
    suspend fun ignoreInvite(@Field("id") id: String): Response<ApiResponse<EmptyResponse>>

    @FormUrlEncoded
    @POST("-/friends/delete")
    suspend fun deleteFriend(@Field("id") id: String): Response<ApiResponse<EmptyResponse>>

    // ---- Welcome ----

    @GET("-/welcome")
    suspend fun getWelcome(): Response<ApiResponse<WelcomeResponse>>

    @POST("-/welcome/seen")
    suspend fun markWelcomeSeen(): Response<ApiResponse<EmptyResponse>>

    // ---- Preferences ----

    @GET("-/preferences/get")
    suspend fun getPreferences(): Response<ApiResponse<PreferenceResponse>>

    @FormUrlEncoded
    @POST("-/preferences/set")
    suspend fun setPreferences(
        @Field("invite_policy") invitePolicy: String,
    ): Response<ApiResponse<EmptyResponse>>

    // ---- Local users (group-membership picker) ----

    @FormUrlEncoded
    @POST("-/users/search")
    suspend fun searchLocalUsers(@Field("search") query: String): Response<ApiResponse<SearchLocalUsersResponse>>

    // ---- Groups ----

    @GET("-/groups/list")
    suspend fun listGroups(): Response<ApiResponse<GetGroupsResponse>>

    @FormUrlEncoded
    @POST("-/groups/get")
    suspend fun getGroup(@Field("id") id: String): Response<ApiResponse<GetGroupResponse>>

    @FormUrlEncoded
    @POST("-/groups/create")
    suspend fun createGroup(
        @Field("id") id: String?,
        @Field("name") name: String,
        @Field("description") description: String?,
    ): Response<ApiResponse<CreateGroupResponse>>

    @FormUrlEncoded
    @POST("-/groups/update")
    suspend fun updateGroup(
        @Field("id") id: String,
        @Field("name") name: String?,
        @Field("description") description: String?,
    ): Response<ApiResponse<EmptyResponse>>

    @FormUrlEncoded
    @POST("-/groups/delete")
    suspend fun deleteGroup(@Field("id") id: String): Response<ApiResponse<EmptyResponse>>

    @FormUrlEncoded
    @POST("-/groups/members/add")
    suspend fun addGroupMember(
        @Field("group") group: String,
        @Field("member") member: String,
        @Field("type") type: String,
    ): Response<ApiResponse<EmptyResponse>>

    @FormUrlEncoded
    @POST("-/groups/members/remove")
    suspend fun removeGroupMember(
        @Field("group") group: String,
        @Field("member") member: String,
    ): Response<ApiResponse<EmptyResponse>>

    // ---- Person profile (entity-scoped) ----

    @GET("{person}/-/information")
    suspend fun getPersonInformation(
        @Path("person") person: String,
    ): Response<ApiResponse<PersonInformation>>

    @GET("{person}/-/style")
    suspend fun getPersonStyle(
        @Path("person") person: String,
    ): Response<ApiResponse<PersonStyle>>

    @FormUrlEncoded
    @POST("{person}/-/profile/set")
    suspend fun setProfile(
        @Path("person") person: String,
        @Field("profile") profile: String,
    ): Response<ApiResponse<EmptyResponse>>

    @FormUrlEncoded
    @POST("{person}/-/style/set")
    suspend fun setAccent(
        @Path("person") person: String,
        @Field("accent") accent: String,
    ): Response<ApiResponse<EmptyResponse>>

    @FormUrlEncoded
    @POST("{person}/-/name/set")
    suspend fun setName(
        @Path("person") person: String,
        @Field("name") name: String,
    ): Response<ApiResponse<EmptyResponse>>

    @FormUrlEncoded
    @POST("{person}/-/privacy/set")
    suspend fun setPrivacy(
        @Path("person") person: String,
        @Field("privacy") privacy: String,
    ): Response<ApiResponse<EmptyResponse>>

    @Multipart
    @POST("{person}/-/avatar/set")
    suspend fun setAvatar(
        @Path("person") person: String,
        @Part file: MultipartBody.Part,
    ): Response<ApiResponse<IdResponse>>

    @Multipart
    @POST("{person}/-/banner/set")
    suspend fun setBanner(
        @Path("person") person: String,
        @Part file: MultipartBody.Part,
    ): Response<ApiResponse<IdResponse>>

    @Multipart
    @POST("{person}/-/favicon/set")
    suspend fun setFavicon(
        @Path("person") person: String,
        @Part file: MultipartBody.Part,
    ): Response<ApiResponse<IdResponse>>
}
