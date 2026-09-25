// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.api

import org.mochios.android.api.ApiResponse
import org.mochios.android.sync.EventComponent
import org.mochios.calendars.model.AccountResponse
import org.mochios.calendars.model.AccountsResponse
import org.mochios.calendars.model.CalendarResponse
import org.mochios.calendars.model.CalendarsResponse
import org.mochios.calendars.model.ChangesResponse
import org.mochios.calendars.model.EventResponse
import org.mochios.calendars.model.EventsResponse
import org.mochios.calendars.model.Hours
import org.mochios.calendars.model.InstancesResponse
import org.mochios.calendars.model.LinkResponse
import org.mochios.calendars.model.Multiweek
import org.mochios.calendars.model.PollResponse
import org.mochios.calendars.model.PreferencesResponse
import org.mochios.calendars.model.GrantResponse
import org.mochios.calendars.model.RemoteResponse
import org.mochios.calendars.model.SplitResponse
import org.mochios.calendars.model.TokenDeleteResponse
import org.mochios.calendars.model.TokenResponse
import org.mochios.calendars.model.TokensResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * The body of `-/events/bounds`: where the calendars' events begin and end,
 * epoch seconds, 0 when there are none. [endless] means something recurs
 * without an end, so there is always another page forward.
 */
data class BoundsResponse(
    val first: Long = 0,
    val last: Long = 0,
    val endless: Boolean = false,
)

/** The body of an action that answers nothing but success. */
data class EmptyResponse(val ok: Boolean = true)

/** Body of `-/events/batch`: up to 500 event ids. */
data class EventsBatchRequest(val events: List<String>)

/**
 * Body of `-/events/create`. [components] is the master `VEVENT` and any
 * overrides beside it; [calendar] defaults to the default calendar when null.
 * [slug] names the event within its calendar: when the calendar already holds
 * one of that name the server answers it instead of creating a second, so the
 * sync adapter can send a create again after its answer was lost.
 */
data class EventCreateRequest(
    val components: List<EventComponent>,
    val calendar: String? = null,
    val slug: String? = null,
)

/**
 * Body of `-/events/update`. [components] replaces the event's whole tree, so
 * it carries every property the server is to keep. [etag] is the copy the
 * caller read: a stale one is refused with 412 rather than overwriting
 * another device's edit. [calendar] moves the event when it differs.
 */
data class EventUpdateRequest(
    val event: String,
    val etag: String? = null,
    val calendar: String? = null,
    val components: List<EventComponent>? = null,
)

/**
 * Body of `-/events/split`: a series cut in two at the occurrence starting
 * at [start], epoch seconds as the server listed it. [components] is the
 * old event's whole tree, ending before that occurrence, and [following]
 * the new event's, going on from it; the server writes the new event first
 * and shortens a `COUNT` itself. [etag] is the copy the caller read, refused
 * with 412 when stale. [calendar] puts the new event in another calendar.
 */
data class EventSplitRequest(
    val event: String,
    val etag: String? = null,
    val start: Long,
    val components: List<EventComponent>,
    val following: List<EventComponent>,
    val calendar: String? = null,
)

/**
 * Body of `-/preferences/set`. Every field is optional: the server validates
 * what it is sent and answers the whole set.
 */
data class PreferencesRequest(
    val hours: Hours? = null,
    val days: List<Int>? = null,
    val multiweek: Multiweek? = null,
    val duration: Int? = null,
    val reminder: Int? = null,
    val view: String? = null,
    val zones: Boolean? = null,
)

/**
 * The calendars app's actions. Every path is relative to `<server>/calendars/`
 * and matches the key in `apps/calendars/app.json`; the form fields match the
 * `a.input(...)` names the matching `action_*` reads, and the JSON bodies the
 * keys it takes from `a.body`.
 */
interface CalendarsApi {

    @GET("-/calendars")
    suspend fun listCalendars(): Response<ApiResponse<CalendarsResponse>>

    @FormUrlEncoded
    @POST("-/calendars/get")
    suspend fun getCalendar(
        @Field("calendar") calendar: String,
    ): Response<ApiResponse<CalendarResponse>>

    @FormUrlEncoded
    @POST("-/calendars/create")
    suspend fun createCalendar(
        // contract-ok: the handler reads name through name_input.
        @Field("name") name: String,
        // contract-ok: the handler reads colour through colour_input.
        @Field("colour") colour: String,
    ): Response<ApiResponse<CalendarResponse>>

    @FormUrlEncoded
    @POST("-/calendars/rename")
    suspend fun renameCalendar(
        @Field("calendar") calendar: String,
        // contract-ok: the handler reads name through name_input.
        @Field("name") name: String,
    ): Response<ApiResponse<CalendarResponse>>

    @FormUrlEncoded
    @POST("-/calendars/colour")
    suspend fun recolourCalendar(
        @Field("calendar") calendar: String,
        // contract-ok: the handler reads colour through colour_input.
        @Field("colour") colour: String,
    ): Response<ApiResponse<CalendarResponse>>

    @FormUrlEncoded
    @POST("-/calendars/delete")
    suspend fun deleteCalendar(
        @Field("calendar") calendar: String,
    ): Response<ApiResponse<EmptyResponse>>

    @FormUrlEncoded
    @POST("-/calendars/subscribe")
    suspend fun subscribeCalendar(
        @Field("url") url: String,
        @Field("name") name: String,
        // contract-ok: the handler reads colour through colour_input.
        @Field("colour") colour: String,
    ): Response<ApiResponse<CalendarResponse>>

    @GET("-/calendars/accounts")
    suspend fun listAccounts(): Response<ApiResponse<AccountsResponse>>

    @FormUrlEncoded
    @POST("-/calendars/account")
    suspend fun addAccount(
        @Field("type") type: String,
        @Field("url") url: String,
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("label") label: String,
    ): Response<ApiResponse<AccountResponse>>

    @FormUrlEncoded
    @POST("-/calendars/remote")
    suspend fun remoteCalendars(
        @Field("account") account: String,
    ): Response<ApiResponse<RemoteResponse>>

    @FormUrlEncoded
    @POST("-/calendars/grant")
    suspend fun grant(
        @Field("account") account: String,
        @Field("provider") provider: String,
        @Field("target") target: String,
        @Field("mode") mode: String,
        @Field("scheme") scheme: String,
        @Field("challenge") challenge: String,
    ): Response<ApiResponse<GrantResponse>>

    @FormUrlEncoded
    @POST("-/calendars/link")
    suspend fun linkCalendar(
        @Field("account") account: String,
        @Field("collection") collection: String,
        @Field("name") name: String,
        // contract-ok: the handler reads colour through colour_input.
        @Field("colour") colour: String,
    ): Response<ApiResponse<CalendarResponse>>

    @FormUrlEncoded
    @POST("-/calendars/poll")
    suspend fun pollCalendar(
        @Field("calendar") calendar: String,
    ): Response<ApiResponse<PollResponse>>

    @GET("-/events")
    suspend fun listEvents(
        // contract-ok: the handler reads start and finish through range_input.
        @Query("start") start: Long,
        // contract-ok: the handler reads start and finish through range_input.
        @Query("finish") finish: Long,
        @Query("calendars") calendars: String,
        @Query("timezone") timezone: String,
    ): Response<ApiResponse<InstancesResponse>>

    @GET("-/events/bounds")
    suspend fun eventBounds(
        @Query("calendars") calendars: String,
    ): Response<ApiResponse<BoundsResponse>>

    @FormUrlEncoded
    @POST("-/events/get")
    suspend fun getEvent(
        @Field("event") event: String,
    ): Response<ApiResponse<EventResponse>>

    @POST("-/events/batch")
    suspend fun batchEvents(@Body request: EventsBatchRequest): Response<ApiResponse<EventsResponse>>

    @POST("-/events/create")
    suspend fun createEvent(@Body request: EventCreateRequest): Response<ApiResponse<EventResponse>>

    @POST("-/events/update")
    suspend fun updateEvent(@Body request: EventUpdateRequest): Response<ApiResponse<EventResponse>>

    @POST("-/events/split")
    suspend fun splitEvent(@Body request: EventSplitRequest): Response<ApiResponse<SplitResponse>>

    @FormUrlEncoded
    @POST("-/events/delete")
    suspend fun deleteEvent(
        @Field("event") event: String,
        @Field("etag") etag: String,
    ): Response<ApiResponse<EmptyResponse>>

    @FormUrlEncoded
    @POST("-/events/changes")
    suspend fun eventChanges(
        @Field("since") since: String,
    ): Response<ApiResponse<ChangesResponse>>

    @FormUrlEncoded
    @POST("-/link")
    suspend fun link(
        @Field("calendar") calendar: String,
        @Field("regenerate") regenerate: String,
    ): Response<ApiResponse<LinkResponse>>

    @FormUrlEncoded
    @POST("-/link/revoke")
    suspend fun revokeLink(
        @Field("calendar") calendar: String,
    ): Response<ApiResponse<EmptyResponse>>

    @GET("-/preferences/get")
    suspend fun getPreferences(): Response<ApiResponse<PreferencesResponse>>

    @POST("-/preferences/set")
    suspend fun setPreferences(@Body request: PreferencesRequest): Response<ApiResponse<PreferencesResponse>>

    @FormUrlEncoded
    @POST("-/token/create")
    suspend fun createToken(
        // contract-ok: the handler reads name through token_name_input.
        @Field("name") name: String,
    ): Response<ApiResponse<TokenResponse>>

    @POST("-/token/list")
    suspend fun listTokens(): Response<ApiResponse<TokensResponse>>

    @FormUrlEncoded
    @POST("-/token/delete")
    suspend fun deleteToken(
        @Field("hash") hash: String,
    ): Response<ApiResponse<TokenDeleteResponse>>
}
