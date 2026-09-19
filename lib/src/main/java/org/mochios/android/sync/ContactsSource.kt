// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.sync

/**
 * A contact as the server holds it: its id, address book, etag and card.
 * [slug] is its name within the book, the one a create gave it or else its
 * id; null from a server that does not say, which matches no row.
 */
data class SyncedContact(
    val id: String,
    val book: String,
    val etag: String,
    val properties: List<ContactProperty>,
    val slug: String? = null,
)

/**
 * What changed since a cursor: [version] is the next cursor, [changed] the
 * ids to fetch and [deleted] the ids to drop. When [reset] is set, [changed]
 * is every contact the server holds and a row outside it is gone: the answer
 * to a cursor of 0, or to one older than the deletions the server remembers.
 */
data class ContactsChanges(
    val version: Long,
    val changed: List<String>,
    val deleted: List<String>,
    val reset: Boolean = false,
)

/** The server changed the contact since the phone's copy was taken (412). */
class SyncConflict : Exception("contact changed on the server")

/** The server no longer holds the contact (404). */
class SyncMissing : Exception("contact not on the server")

/** The session or app token is no longer accepted (401, 403). */
class SyncAuthorization : Exception("not allowed")

/**
 * The server side of contacts sync: the people app's JSON actions, called with
 * the app token the client already mints. The adapter never speaks vCard or
 * DAV; the property list is the wire format both ways.
 *
 * Implementations translate HTTP status into the exceptions above and let
 * transport failures (an [java.io.IOException]) propagate as they are.
 */
interface ContactsSource {

    /** Changes since [since], 0 for the whole address book. */
    suspend fun changes(since: Long): ContactsChanges

    /** The full contacts for up to [BATCH] ids; ids the server lacks are absent. */
    suspend fun fetch(ids: List<String>): List<SyncedContact>

    /**
     * Creates a contact from the managed properties, in the default book,
     * named [slug]. When the book already holds a contact of that name the
     * server answers it, as it holds it, instead of making a second.
     */
    suspend fun create(properties: List<ContactProperty>, slug: String): SyncedContact

    /** Replaces the managed properties, refused with [SyncConflict] on a stale [etag]. */
    suspend fun update(id: String, etag: String, properties: List<ContactProperty>): SyncedContact

    /** Deletes the contact, refused with [SyncConflict] on a stale [etag]. */
    suspend fun delete(id: String, etag: String)

    companion object {
        /** Most ids one [fetch] may carry. */
        const val BATCH = 500
    }
}
