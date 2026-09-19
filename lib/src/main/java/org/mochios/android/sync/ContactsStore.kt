// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.sync

/**
 * One `Data` row of a raw contact: its MIME type, its text columns keyed by
 * column name (`data1` .. `data15`, `data_sync1`), and for a photo the image
 * bytes that go in its blob column.
 */
class DataRow(
    val mimetype: String,
    val values: Map<String, String?>,
    val blob: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is DataRow && mimetype == other.mimetype && values == other.values &&
            (blob?.contentEquals(other.blob) ?: (other.blob == null))

    override fun hashCode(): Int = 31 * (31 * mimetype.hashCode() + values.hashCode()) + (blob?.contentHashCode() ?: 0)

    override fun toString(): String = "DataRow($mimetype, $values${if (blob != null) ", ${blob.size} bytes" else ""})"
}

/**
 * A raw contact of the account as the provider indexes it. [local] is the
 * provider's row id; [remote] the server's contact id (`SOURCE_ID`), null for
 * a contact created on the phone and not yet uploaded; [etag] and [book] are
 * the server's, carried in `SYNC1` and `SYNC2`. [version] is the provider's
 * `VERSION`, which moves on every edit, so an upload can tell whether the
 * phone changed the row again while it was in flight. [slug] is the name the
 * phone gave a contact it made, carried in `SYNC3`: null until its first
 * create, then sent with every create of the row.
 */
data class StoredContact(
    val local: Long,
    val remote: String?,
    val etag: String?,
    val book: String?,
    val dirty: Boolean,
    val deleted: Boolean,
    val version: Long = 0,
    val slug: String? = null,
)

/**
 * The phone side of contacts sync: the account's rows in the contacts
 * provider, and the sync state that carries the server cursor. Every write
 * is made as the sync adapter, so nothing here marks a row dirty.
 */
interface ContactsStore {

    /** The server cursor of the last completed download, 0 before the first. */
    fun version(): Long

    fun version(value: Long)

    /** Every raw contact of the account, deleted ones included. */
    fun contacts(): List<StoredContact>

    /** The managed properties a raw contact's `Data` rows express. */
    fun properties(local: Long): List<ContactProperty>

    /** A new raw contact for a server contact, clean. */
    fun insert(contact: SyncedContact)

    /**
     * Overwrites a raw contact's managed `Data` rows and server fields from
     * [contact], clearing its dirty flag. Rows the sync does not own — a photo
     * set on the phone, a group membership, an anniversary — are left alone.
     */
    fun replace(local: Long, contact: SyncedContact)

    /**
     * Stores the server's id, etag and book after an upload. The dirty flag
     * clears only while the row is still at [version]: an edit made on the
     * phone during the upload keeps it dirty, so it goes up next time rather
     * than being taken for uploaded. A null [version] leaves the flag as it
     * is, for the upload to resolve the row.
     */
    fun bind(local: Long, contact: SyncedContact, version: Long?)

    /**
     * Names a raw contact made on the phone, in `SYNC3`, before its first
     * create goes out. Every create of the row carries the name, so one sent
     * again after its answer was lost finds the contact the first one made.
     */
    fun slug(local: Long, value: String)

    /**
     * Replaces a raw contact with a new one for [contact], in one step, so
     * nothing between the two leaves the server's contact off the phone.
     */
    fun recreate(local: Long, contact: SyncedContact)

    /** Removes the raw contact for good. */
    fun remove(local: Long)
}
