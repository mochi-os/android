// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.sync

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.mochios.android.api.ApiException
import org.mochios.android.api.ApiResponse
import org.mochios.android.api.MochiError
import org.mochios.android.api.unwrap
import org.mochios.android.auth.AuthRepository
import org.mochios.android.sync.CalendarsChanges
import org.mochios.android.sync.CalendarsSource
import org.mochios.android.sync.EventComponent
import org.mochios.android.sync.SyncAuthorization
import org.mochios.android.sync.SyncConflict
import org.mochios.android.sync.SyncMissing
import org.mochios.android.sync.SyncRefused
import org.mochios.android.sync.SyncedCalendar
import org.mochios.android.sync.SyncedEvent
import org.mochios.calendars.api.CalendarsApi
import org.mochios.calendars.api.EventCreateRequest
import org.mochios.calendars.api.EventUpdateRequest
import org.mochios.calendars.api.EventsBatchRequest
import org.mochios.calendars.model.Calendar
import org.mochios.calendars.model.Event
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [CalendarsSource] over the calendars app's JSON actions, for the calendar
 * sync adapter. Runs in the background with no screen open, so it mints the
 * app token itself rather than relying on one a screen left in the cache.
 */
@Singleton
class CalendarsEventSource @Inject constructor(
    private val api: CalendarsApi,
    private val authRepository: AuthRepository,
) : CalendarsSource {

    override suspend fun calendars(): List<SyncedCalendar> =
        call { api.listCalendars() }.calendars.map { it.synced() }

    override suspend fun changes(since: Long): CalendarsChanges {
        val data = call { api.eventChanges(since.toString()) }
        return CalendarsChanges(data.version, data.changed, data.deleted, data.reset)
    }

    override suspend fun fetch(ids: List<String>): List<SyncedEvent> =
        call { api.batchEvents(EventsBatchRequest(ids)) }.events.map { it.synced() }

    override suspend fun create(
        calendar: String,
        components: List<EventComponent>,
        slug: String,
    ): SyncedEvent =
        call { api.createEvent(EventCreateRequest(components, calendar, slug)) }.event.synced()

    override suspend fun update(
        id: String,
        etag: String,
        calendar: String?,
        components: List<EventComponent>,
    ): SyncedEvent =
        call { api.updateEvent(EventUpdateRequest(id, etag, calendar, components)) }.event.synced()

    override suspend fun delete(id: String, etag: String) {
        call { api.deleteEvent(id, etag) }
    }

    /**
     * One request, its status turned into what the sync cycle acts on: 412 a
     * conflict, 404 an event gone, 401 and 403 a hard stop, a server failure
     * or throttle a transport failure the framework retries, and any other
     * refusal the server's own message.
     */
    private suspend fun <T> call(request: suspend () -> Response<ApiResponse<T>>): T {
        authorize()
        val response = request()
        try {
            return response.unwrap()
        } catch (e: ApiException) {
            throw when (e.code) {
                401, 403 -> SyncAuthorization()
                404 -> SyncMissing()
                412 -> SyncConflict()
                408, 429, in 500..599 -> IOException("HTTP ${e.code}")
                else -> SyncRefused(e.message)
            }
        } catch (e: MochiError.NetworkError) {
            // A body that is not the server's JSON: a proxy or gateway.
            throw IOException(e)
        }
    }

    /** A fresh app token in the cache the module's client reads from. */
    private suspend fun authorize() {
        authRepository.fetchToken(APP).getOrElse { error ->
            throw when {
                error is ApiException && (error.code == 401 || error.code == 403) -> SyncAuthorization()
                error is IOException -> error
                else -> IOException(error)
            }
        }
    }

    private fun Calendar.synced() = SyncedCalendar(id, name, colour, kind, readonly, default)

    private fun Event.synced() = SyncedEvent(id, calendar, etag, components, slug.ifEmpty { null })

    private companion object {
        const val APP = "calendars"
    }
}

/** Binds [CalendarsEventSource] as the calendar sync adapter's transport. */
@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarsSourceModule {

    @Binds
    @Singleton
    abstract fun bindCalendarsSource(impl: CalendarsEventSource): CalendarsSource
}
