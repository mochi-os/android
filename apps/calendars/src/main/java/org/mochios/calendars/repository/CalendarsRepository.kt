// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.repository

import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mochios.android.api.ApiError
import org.mochios.android.api.ApiException
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.api.unwrap
import org.mochios.android.sync.EventComponent
import org.mochios.calendars.api.CalendarsApi
import org.mochios.calendars.api.EventCreateRequest
import org.mochios.calendars.api.EventSplitRequest
import org.mochios.calendars.api.EventUpdateRequest
import org.mochios.calendars.api.EventsBatchRequest
import org.mochios.calendars.api.MenuApi
import org.mochios.calendars.api.PreferencesRequest
import org.mochios.calendars.model.AccountsResponse
import org.mochios.calendars.model.Calendar
import org.mochios.calendars.model.CalendarAccount
import org.mochios.calendars.model.DeviceToken
import org.mochios.calendars.model.TokenResponse
import org.mochios.calendars.model.Event
import org.mochios.calendars.model.Instance
import org.mochios.calendars.model.LinkResponse
import org.mochios.calendars.model.Preferences
import org.mochios.calendars.model.GrantResponse
import org.mochios.calendars.model.RemoteCalendar
import org.mochios.calendars.ui.calendar.Bounds
import org.mochios.calendars.ui.editor.excluded
import org.mochios.calendars.ui.editor.truncated
import javax.inject.Inject
import javax.inject.Singleton

/** Parsed body of a `permission_required` 403 from `-/calendars/subscribe`. */
private data class PermissionRequiredBody(
    val app: String? = null,
    val error: String? = null,
    val permission: String? = null,
)

/**
 * `-/calendars/subscribe` answered `permission_required`: [app] must be
 * granted [permission] — fetching from the URL's host — before the server
 * will subscribe.
 */
class PermissionRequiredException(
    val app: String,
    val permission: String,
) : Exception("permission_required: $permission")

/** The server changed the event since the phone's copy was read (412). */
class EventChangedException : Exception("event changed on the server")

/**
 * Everything the calendars screens call. Keeps the calendar list cached, so
 * the drawer on one screen and the views on another read the same rows, and
 * announces every change through [calendarsChanged] so a screen that did not
 * make it reloads.
 */
@Singleton
class CalendarsRepository @Inject constructor(
    private val api: CalendarsApi,
    private val menuApi: MenuApi,
) {

    private val _calendars = MutableStateFlow<List<Calendar>>(emptyList())

    /** The calendars as last read, for screens that open without one. */
    val calendars: StateFlow<List<Calendar>> = _calendars.asStateFlow()

    private val _calendarsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    /** Fires when a calendar is created, renamed, recoloured, subscribed or deleted. */
    val calendarsChanged: SharedFlow<Unit> = _calendarsChanged.asSharedFlow()

    private val _eventsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    /** Fires when an event is created, changed or deleted from this client. */
    val eventsChanged: SharedFlow<Unit> = _eventsChanged.asSharedFlow()

    // ---- calendars ----

    suspend fun listCalendars(): List<Calendar> = call {
        api.listCalendars().unwrap().calendars
    }.also { _calendars.value = it }

    suspend fun createCalendar(name: String, colour: String): Calendar = call {
        api.createCalendar(name, colour).unwrap().calendar
    }.also { announce() }

    suspend fun renameCalendar(calendar: String, name: String): Calendar = call {
        api.renameCalendar(calendar, name).unwrap().calendar
    }.also { announce() }

    suspend fun recolourCalendar(calendar: String, colour: String): Calendar = call {
        api.recolourCalendar(calendar, colour).unwrap().calendar
    }.also { announce() }

    suspend fun deleteCalendar(calendar: String) {
        call { api.deleteCalendar(calendar).unwrap() }
        announce()
    }

    /**
     * Subscribes to an external calendar. The server asks for consent to
     * fetch from the URL's host the first time, which arrives as a
     * `permission_required` 403 and is raised as [PermissionRequiredException]
     * so the caller can offer to grant it and try again.
     */
    suspend fun subscribeCalendar(url: String, name: String, colour: String): Calendar {
        val response = try {
            api.subscribeCalendar(url, name, colour)
        } catch (e: Exception) {
            throw e.toMochiError()
        }
        if (response.isSuccessful) {
            val calendar = response.body()?.data?.calendar ?: throw MochiError.Unknown()
            announce()
            return calendar
        }
        val body = response.errorBody()?.string()
        if (response.code() == 403 && !body.isNullOrBlank()) {
            val required = runCatching {
                Gson().fromJson(body, PermissionRequiredBody::class.java)
            }.getOrNull()
            if (required?.error == "permission_required" &&
                !required.app.isNullOrEmpty() && !required.permission.isNullOrEmpty()
            ) {
                throw PermissionRequiredException(required.app, required.permission)
            }
        }
        val trimmed = body?.trimStart()
        val error = if (!trimmed.isNullOrEmpty() && trimmed.startsWith("{")) {
            runCatching { Gson().fromJson(trimmed, ApiError::class.java) }.getOrNull()
        } else {
            null
        }
        throw error?.let { ApiException(response.code(), it).toMochiError() } ?: MochiError.NetworkError()
    }

    /**
     * The connected accounts a calendar can be linked through, with the
     * capabilities each holds now, and the providers the server can grant a
     * new account from.
     */
    suspend fun listAccounts(): AccountsResponse = call {
        api.listAccounts().unwrap()
    }

    /**
     * Connects an account a calendar can be linked through: an Apple ID with
     * an app-specific password, or a CalDAV server with a login. [url] is the
     * server's address, empty for Apple, and [label] what the account is
     * called here, empty for none. The server tries the account against its
     * own server before keeping it, so a wrong password is refused here.
     */
    suspend fun addAccount(
        type: String,
        url: String,
        username: String,
        password: String,
        label: String,
    ): CalendarAccount = call {
        api.addAccount(type, url, username, password, label).unwrap().account
    }

    /** The calendars an account's server offers, to link one of. */
    suspend fun remoteCalendars(account: String): List<RemoteCalendar> = call {
        api.remoteCalendars(account).unwrap().calendars
    }

    /**
     * Starts the provider's consent that grants calendar access to [account],
     * or, with none, to whichever account of [provider] the user picks. The
     * consent opens in the system browser and returns on the app's deep link,
     * bound by [challenge], the app's PKCE challenge; the grant lands at the
     * exchange with the verifier.
     */
    suspend fun grant(account: String, provider: String, challenge: String): GrantResponse = call {
        api.grant(account, provider, "/calendars/", "mobile", "mochi", challenge).unwrap()
    }

    /**
     * Links a collection on the account's server as a calendar here. The
     * server pulls its events before answering, so the calendar arrives
     * whole.
     */
    suspend fun linkCalendar(
        account: String,
        collection: String,
        name: String,
        colour: String,
    ): Calendar = call {
        api.linkCalendar(account, collection, name, colour).unwrap().calendar
    }.also { announce() }

    /**
     * Fetches a subscription or syncs a linked calendar now, answering how
     * many events moved.
     */
    suspend fun pollCalendar(calendar: String): Int = call {
        api.pollCalendar(calendar).unwrap().changed
    }.also { announce() }

    /** Resolve a permission key to its human label. */
    suspend fun permissionName(permission: String): String = call {
        menuApi.permissionName(permission).unwrap().name
    }

    /** Grant [app] the given [permission], returning the resulting status. */
    suspend fun grantPermission(app: String, permission: String): String = call {
        menuApi.grantPermission(app, permission).unwrap().status
    }

    // ---- events ----

    /**
     * Every occurrence between [start] and [finish], epoch seconds, in the
     * calendars named; an empty [calendars] means all of them. [timezone] is
     * the IANA zone the phone draws in, so the server reads floating times
     * and day boundaries the way the views do. The second of the pair says
     * the range held more than the server will list.
     */
    suspend fun listEvents(
        start: Long,
        finish: Long,
        calendars: List<String>,
        timezone: String,
    ): Pair<List<Instance>, Boolean> = call {
        val body = api.listEvents(start, finish, calendars.joinToString(","), timezone).unwrap()
        body.instances to body.truncated
    }

    /**
     * Where the named calendars' events begin and end; an empty [calendars]
     * means all of them. The list view pages between these, and pages on
     * regardless when something recurs without an end.
     */
    suspend fun eventBounds(calendars: List<String>): Bounds = call {
        val body = api.eventBounds(calendars.joinToString(",")).unwrap()
        Bounds(body.first, body.last, body.endless)
    }

    suspend fun getEvent(event: String): Event = call { api.getEvent(event).unwrap().event }

    suspend fun batchEvents(events: List<String>): List<Event> = call {
        api.batchEvents(EventsBatchRequest(events)).unwrap().events
    }

    suspend fun createEvent(calendar: String?, components: List<EventComponent>, slug: String? = null): Event =
        call {
            api.createEvent(EventCreateRequest(components, calendar, slug)).unwrap().event
        }.also { _eventsChanged.tryEmit(Unit) }

    suspend fun updateEvent(
        event: String,
        etag: String?,
        calendar: String?,
        components: List<EventComponent>?,
    ): Event = call {
        api.updateEvent(EventUpdateRequest(event, etag, calendar, components)).unwrap().event
    }.also { _eventsChanged.tryEmit(Unit) }

    /**
     * Cuts a series in two at the occurrence starting at [start], epoch
     * seconds as the server listed it: the event is rewritten as
     * [components], ending before that occurrence, and a new event is
     * created from [following], in [calendar] when given. Both halves go in
     * one call, so the series is never left with both or neither. Answers
     * the old event and the new one.
     */
    suspend fun splitEvent(
        event: String,
        etag: String?,
        start: Long,
        components: List<EventComponent>,
        following: List<EventComponent>,
        calendar: String? = null,
    ): Pair<Event, Event> = call {
        val body = api.splitEvent(EventSplitRequest(event, etag, start, components, following, calendar)).unwrap()
        body.event to body.following
    }.also { _eventsChanged.tryEmit(Unit) }

    suspend fun deleteEvent(event: String, etag: String) {
        call { api.deleteEvent(event, etag).unwrap() }
        _eventsChanged.tryEmit(Unit)
    }

    /**
     * Removes one occurrence of a recurring event: its override goes and its
     * start joins the master's `EXDATE`, which is how an occurrence is taken
     * out of a series. [occurrence] is the occurrence's own start, epoch
     * seconds. A 412 means the server moved on, so the event is read again
     * and the exclusion applied to that copy.
     */
    suspend fun excludeOccurrence(event: String, occurrence: Long) {
        val current = getEvent(event)
        try {
            updateEvent(event, current.etag, null, excluded(current.components, occurrence))
        } catch (_: EventChangedException) {
            val fresh = getEvent(event)
            updateEvent(event, fresh.etag, null, excluded(fresh.components, occurrence))
        }
    }

    /**
     * Removes one occurrence of a recurring event and every one after it:
     * the series is cut to end just before it. [occurrence] is the start an
     * override of it is matched by. The series' first occurrence has nothing
     * before it, so removing from there deletes the event. A 412 means the
     * server moved on, so the event is read again and the cut applied to
     * that copy.
     */
    suspend fun truncateEvent(event: String, occurrence: Long) {
        suspend fun cut(current: Event) {
            val components = truncated(current.components, occurrence)
            if (components == null) {
                deleteEvent(event, current.etag)
            } else {
                updateEvent(event, current.etag, null, components)
            }
        }
        try {
            cut(getEvent(event))
        } catch (_: EventChangedException) {
            cut(getEvent(event))
        }
    }

    // ---- preferences, links and devices ----

    suspend fun getPreferences(): Preferences = call { api.getPreferences().unwrap().preferences }

    suspend fun setPreferences(request: PreferencesRequest): Preferences = call {
        api.setPreferences(request).unwrap().preferences
    }

    /** The ICS link for a calendar; [regenerate] revokes the old token and mints a new one. */
    suspend fun link(calendar: String, regenerate: Boolean): LinkResponse = call {
        api.link(calendar, if (regenerate) "1" else "").unwrap()
    }

    suspend fun revokeLink(calendar: String) {
        call { api.revokeLink(calendar).unwrap() }
    }

    suspend fun createToken(name: String): TokenResponse = call { api.createToken(name).unwrap() }

    suspend fun listTokens(): List<DeviceToken> = call { api.listTokens().unwrap().tokens }

    suspend fun deleteToken(hash: String) {
        call { api.deleteToken(hash).unwrap() }
    }

    private fun announce() {
        _calendarsChanged.tryEmit(Unit)
    }

    /**
     * One request, with a 412 raised as [EventChangedException] so the caller
     * can reload and retry rather than showing the user a raw precondition
     * failure, and everything else as the standard error.
     */
    private inline fun <T> call(request: () -> T): T = try {
        request()
    } catch (e: ApiException) {
        if (e.code == 412) throw EventChangedException() else throw e.toMochiError()
    } catch (e: Exception) {
        throw e.toMochiError()
    }
}
