// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.sync

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.mochios.android.api.ApiException
import org.mochios.android.api.ApiResponse
import org.mochios.android.api.MochiError
import org.mochios.android.api.unwrap
import org.mochios.android.auth.AuthRepository
import org.mochios.android.sync.ContactProperty
import org.mochios.android.sync.ContactsChanges
import org.mochios.android.sync.ContactsSource
import org.mochios.android.sync.SyncAuthorization
import org.mochios.android.sync.SyncConflict
import org.mochios.android.sync.SyncMissing
import org.mochios.android.sync.SyncRefused
import org.mochios.android.sync.SyncedContact
import org.mochios.people.api.ContactRequest
import org.mochios.people.api.ContactUpdateRequest
import org.mochios.people.api.ContactsBatchRequest
import org.mochios.people.api.PeopleApi
import org.mochios.people.model.Contact
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ContactsSource] over the people app's JSON actions, for the contacts sync
 * adapter. Runs in the background with no screen open, so it mints the app
 * token itself rather than relying on one a screen left in the cache.
 */
@Singleton
class PeopleContactsSource @Inject constructor(
    private val api: PeopleApi,
    private val authRepository: AuthRepository,
) : ContactsSource {

    override suspend fun changes(since: Long): ContactsChanges {
        val data = call { api.contactsChanges(since.toString()) }
        return ContactsChanges(data.version, data.changed, data.deleted, data.reset)
    }

    override suspend fun fetch(ids: List<String>): List<SyncedContact> =
        call { api.contactsBatch(ContactsBatchRequest(ids)) }.contacts.map { it.synced() }

    override suspend fun create(properties: List<ContactProperty>, slug: String): SyncedContact =
        call { api.createContact(ContactRequest(properties, slug = slug)) }.contact.synced()

    override suspend fun update(id: String, etag: String, properties: List<ContactProperty>): SyncedContact =
        call { api.updateContact(ContactUpdateRequest(id, etag, properties)) }.contact.synced()

    override suspend fun delete(id: String, etag: String) {
        call { api.deleteContact(id, etag) }
    }

    /**
     * One request, its status turned into what the sync cycle acts on: 412
     * a conflict, 404 a contact gone, 401 and 403 a hard stop, a server
     * failure or throttle a transport failure the framework retries, and any
     * other refusal the server's own message.
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

    private fun Contact.synced() = SyncedContact(id, book, etag, card.orEmpty(), slug.ifEmpty { null })

    private companion object {
        const val APP = "people"
    }
}

/** Binds [PeopleContactsSource] as the contacts sync adapter's transport. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ContactsSourceModule {

    @Binds
    @Singleton
    abstract fun bindContactsSource(impl: PeopleContactsSource): ContactsSource
}
