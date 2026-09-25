// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.api

import okhttp3.MultipartBody
import org.mochios.android.api.ApiResponse
import org.mochios.android.sync.ContactProperty
import org.mochios.people.model.Book
import org.mochios.people.model.Contact
import org.mochios.people.model.DeviceToken
import org.mochios.people.model.FriendInvite
import org.mochios.people.model.Group
import org.mochios.people.model.GroupMember
import org.mochios.people.model.LocalUser
import org.mochios.people.model.PersonInformation
import org.mochios.people.model.PersonStyle
import org.mochios.people.model.User
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

// Response wrappers: the inner shape of each action's `{"data": ...}` envelope,
// which ApiResponse<T> already unwraps.

data class ContactsListResponse(
    val contacts: List<Contact> = emptyList(),
    val received: List<FriendInvite> = emptyList(),
    val sent: List<FriendInvite> = emptyList(),
)

data class ContactResponse(val contact: Contact = Contact())

data class BooksResponse(val books: List<Book> = emptyList())

data class BookResponse(val book: Book = Book())

/**
 * Body of `-/contacts/create`. [properties] is the whole managed set the
 * editor shows; [person] links the contact to a Mochi person and [book] picks
 * the address book, both defaulting server-side when null. [slug] names the
 * contact within its book: when the book already holds one of that name the
 * server answers it instead of creating a second, so the sync adapter can
 * send a create again after its answer was lost.
 */
data class ContactRequest(
    val properties: List<ContactProperty>,
    val person: String? = null,
    val book: String? = null,
    val slug: String? = null,
)

/**
 * Body of `-/contacts/update`. Sending [properties] replaces every managed
 * property and keeps the rest of the card, so it carries the full set the
 * editor shows. [etag] is the card read back from the server: a stale one is
 * refused with 412 rather than overwriting another device's edit.
 */
data class ContactUpdateRequest(
    val contact: String,
    val etag: String? = null,
    val properties: List<ContactProperty>? = null,
    val book: String? = null,
)

/**
 * `-/contacts/changes`: [version] is the cursor to pass next time, [changed]
 * the contacts to fetch and [deleted] the ones to drop. [reset] says
 * [changed] lists every contact, so the caller drops whatever it lacks.
 */
data class ContactsChangesResponse(
    val version: Long = 0,
    val changed: List<String> = emptyList(),
    val deleted: List<String> = emptyList(),
    val reset: Boolean = false,
)

/** Body of `-/contacts/batch`: up to 500 contact ids. */
data class ContactsBatchRequest(val contacts: List<String>)

data class ContactsBatchResponse(val contacts: List<Contact> = emptyList())

/** `-/token/create`: the device's password, returned this once. */
data class TokenResponse(val token: String = "")

data class TokensResponse(val tokens: List<DeviceToken> = emptyList())

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
    @com.google.gson.annotations.SerializedName("policy")
    val invitePolicy: String = "notify"
)

data class IdResponse(val id: String = "")

data class EmptyResponse(val ok: Boolean = true)

interface PeopleApi {

    // ---- Contacts ----

    @GET("-/contacts")
    suspend fun listContacts(
        @Query("book") book: String? = null,
    ): Response<ApiResponse<ContactsListResponse>>

    @FormUrlEncoded
    @POST("-/contacts/get")
    suspend fun getContact(@Field("contact") contact: String): Response<ApiResponse<ContactResponse>>

    @POST("-/contacts/create")
    suspend fun createContact(@Body request: ContactRequest): Response<ApiResponse<ContactResponse>>

    @POST("-/contacts/update")
    suspend fun updateContact(@Body request: ContactUpdateRequest): Response<ApiResponse<ContactResponse>>

    /** A stale [etag] is refused with 412; null deletes whatever the server holds. */
    @FormUrlEncoded
    @POST("-/contacts/delete")
    suspend fun deleteContact(
        @Field("contact") contact: String,
        @Field("etag") etag: String? = null,
    ): Response<ApiResponse<EmptyResponse>>

    @FormUrlEncoded
    @POST("-/contacts/search")
    suspend fun searchDirectory(@Field("search") search: String): Response<ApiResponse<SearchUsersResponse>>

    // ---- Contacts sync ----

    /** What changed since the cursor [since], "0" for every contact. */
    @FormUrlEncoded
    @POST("-/contacts/changes")
    suspend fun contactsChanges(@Field("since") since: String): Response<ApiResponse<ContactsChangesResponse>>

    /** Several contacts in full; ids the server does not hold are left out. */
    @POST("-/contacts/batch")
    suspend fun contactsBatch(@Body request: ContactsBatchRequest): Response<ApiResponse<ContactsBatchResponse>>

    // ---- Device tokens (the CardDAV password a device holds) ----

    @FormUrlEncoded
    @POST("-/token/create")
    // contract-ok: the handler reads name through its token_name_input helper.
    suspend fun createToken(@Field("name") name: String): Response<ApiResponse<TokenResponse>>

    @POST("-/token/list")
    suspend fun listTokens(): Response<ApiResponse<TokensResponse>>

    @FormUrlEncoded
    @POST("-/token/delete")
    suspend fun deleteToken(@Field("hash") hash: String): Response<ApiResponse<EmptyResponse>>

    // ---- Address books ----

    @GET("-/books")
    suspend fun listBooks(): Response<ApiResponse<BooksResponse>>

    @FormUrlEncoded
    @POST("-/books/create")
    // contract-ok: the handler reads name through its book_name_input helper.
    suspend fun createBook(@Field("name") name: String): Response<ApiResponse<BookResponse>>

    @FormUrlEncoded
    @POST("-/books/rename")
    suspend fun renameBook(
        @Field("book") book: String,
        // contract-ok: the handler reads name through its book_name_input helper.
        @Field("name") name: String,
    ): Response<ApiResponse<EmptyResponse>>

    @FormUrlEncoded
    @POST("-/books/delete")
    suspend fun deleteBook(@Field("book") book: String): Response<ApiResponse<EmptyResponse>>

    // ---- Friendship ----

    @FormUrlEncoded
    @POST("-/friends/invite")
    suspend fun inviteFriend(
        // contract-ok: the handler reads person through its person_input helper.
        @Field("person") person: String,
        @Field("name") name: String,
        /** An existing card to link to the person first, so it becomes the friend itself. */
        @Field("contact") contact: String? = null,
    ): Response<ApiResponse<EmptyResponse>>

    @FormUrlEncoded
    @POST("-/friends/accept")
    // contract-ok: the handler reads person through its person_input helper.
    suspend fun acceptInvite(@Field("person") person: String): Response<ApiResponse<EmptyResponse>>

    @FormUrlEncoded
    @POST("-/friends/ignore")
    // contract-ok: the handler reads person through its person_input helper.
    suspend fun ignoreInvite(@Field("person") person: String): Response<ApiResponse<EmptyResponse>>

    /** Ends the friendship and keeps the contact. */
    @FormUrlEncoded
    @POST("-/friends/remove")
    // contract-ok: the handler reads person through its person_input helper.
    suspend fun removeFriend(@Field("person") person: String): Response<ApiResponse<EmptyResponse>>

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
        @Field("policy") invitePolicy: String,
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
