// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.sync

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentProviderOperation
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import android.provider.ContactsContract.Settings
import android.provider.SyncStateContract
import java.io.ByteArrayOutputStream

/**
 * [ContactsStore] over the contacts provider, for one account. Every URI
 * carries [ContactsContract.CALLER_IS_SYNCADAPTER], so the provider neither
 * marks these writes dirty nor hides deleted rows from the queries.
 */
class ProviderContactsStore(
    private val provider: ContentProviderClient,
    private val account: Account,
) : ContactsStore {

    /**
     * Makes the account's contacts visible in the Contacts app. They belong
     * to no group, and a Contacts app hides ungrouped contacts of an account
     * unless the account's settings say otherwise.
     */
    fun prepare() {
        val values = ContentValues().apply {
            put(Settings.ACCOUNT_NAME, account.name)
            put(Settings.ACCOUNT_TYPE, account.type)
            put(Settings.UNGROUPED_VISIBLE, 1)
            put(Settings.SHOULD_SYNC, 1)
        }
        val updated = provider.update(
            Settings.CONTENT_URI.adapter(),
            values,
            "${Settings.ACCOUNT_NAME}=? and ${Settings.ACCOUNT_TYPE}=?",
            arrayOf(account.name, account.type),
        )
        if (updated == 0) provider.insert(Settings.CONTENT_URI.adapter(), values)
    }

    override fun version(): Long =
        SyncStateContract.Helpers.get(provider, ContactsContract.SyncState.CONTENT_URI, account)
            ?.toString(Charsets.UTF_8)?.trim()?.toLongOrNull() ?: 0L

    override fun version(value: Long) {
        SyncStateContract.Helpers.set(
            provider,
            ContactsContract.SyncState.CONTENT_URI,
            account,
            value.toString().toByteArray(Charsets.UTF_8),
        )
    }

    override fun contacts(): List<StoredContact> {
        val out = mutableListOf<StoredContact>()
        provider.query(
            RawContacts.CONTENT_URI.adapter(),
            arrayOf(
                RawContacts._ID,
                RawContacts.SOURCE_ID,
                RawContacts.SYNC1,
                RawContacts.SYNC2,
                RawContacts.DIRTY,
                RawContacts.DELETED,
                RawContacts.VERSION,
                RawContacts.SYNC3,
            ),
            "${RawContacts.ACCOUNT_NAME}=? and ${RawContacts.ACCOUNT_TYPE}=?",
            arrayOf(account.name, account.type),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                out.add(
                    StoredContact(
                        local = cursor.getLong(0),
                        remote = cursor.getString(1)?.takeIf { it.isNotBlank() },
                        etag = cursor.getString(2),
                        book = cursor.getString(3),
                        dirty = cursor.getInt(4) != 0,
                        deleted = cursor.getInt(5) != 0,
                        version = cursor.getLong(6),
                        slug = cursor.getString(7)?.takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
        return out
    }

    override fun properties(local: Long): List<ContactProperty> {
        val rows = mutableListOf<DataRow>()
        val kinds = ContactsMapping.readable().toList()
        provider.query(
            Data.CONTENT_URI.adapter(),
            arrayOf(Data.MIMETYPE) + COLUMNS,
            "${Data.RAW_CONTACT_ID}=? and ${Data.MIMETYPE} in (${kinds.joinToString(",") { "?" }})",
            (listOf(local.toString()) + kinds).toTypedArray(),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val values = mutableMapOf<String, String?>()
                for ((index, column) in COLUMNS.withIndex()) {
                    // A kind may keep a blob in a column the managed ones use
                    // for text; reading it as text throws.
                    if (cursor.getType(index + 1) == Cursor.FIELD_TYPE_BLOB) continue
                    values[column] = cursor.getString(index + 1)
                }
                rows.add(DataRow(cursor.getString(0).orEmpty(), values))
            }
        }
        return ContactsMapping.properties(rows)
    }

    override fun insert(contact: SyncedContact) {
        val batch = arrayListOf<ContentProviderOperation>()
        creation(batch, contact)
        provider.applyBatch(batch)
    }

    override fun recreate(local: Long, contact: SyncedContact) {
        val batch = arrayListOf(
            ContentProviderOperation.newDelete(RawContacts.CONTENT_URI.adapter())
                .withSelection("${RawContacts._ID}=?", arrayOf(local.toString()))
                .build(),
        )
        creation(batch, contact)
        provider.applyBatch(batch)
    }

    /** Appends the operations that create a raw contact and its rows for [contact]. */
    private fun creation(batch: ArrayList<ContentProviderOperation>, contact: SyncedContact) {
        val index = batch.size
        batch.add(
            ContentProviderOperation.newInsert(RawContacts.CONTENT_URI.adapter())
                .withValue(RawContacts.ACCOUNT_NAME, account.name)
                .withValue(RawContacts.ACCOUNT_TYPE, account.type)
                .withValues(server(contact))
                .build(),
        )
        for (row in rows(contact)) {
            batch.add(
                ContentProviderOperation.newInsert(Data.CONTENT_URI.adapter())
                    .withValueBackReference(Data.RAW_CONTACT_ID, index)
                    .withValues(row.toContentValues())
                    .build(),
            )
        }
    }

    override fun replace(local: Long, contact: SyncedContact) {
        val batch = arrayListOf<ContentProviderOperation>()
        val kinds = ContactsMapping.kinds().toList()
        batch.add(
            ContentProviderOperation.newDelete(Data.CONTENT_URI.adapter())
                .withSelection(
                    "${Data.RAW_CONTACT_ID}=? and (${Data.MIMETYPE} in (${kinds.joinToString(",") { "?" }}) or " +
                        "(${Data.MIMETYPE}=? and ${Event.TYPE}=?) or (${Data.MIMETYPE}=? and ${Data.SYNC1}=?))",
                    (
                        listOf(local.toString()) + kinds + listOf(
                            Event.CONTENT_ITEM_TYPE,
                            Event.TYPE_BIRTHDAY.toString(),
                            Photo.CONTENT_ITEM_TYPE,
                            ContactsMapping.PHOTO_MARKER,
                        )
                    ).toTypedArray(),
                )
                .build(),
        )
        for (row in rows(contact)) {
            batch.add(
                ContentProviderOperation.newInsert(Data.CONTENT_URI.adapter())
                    .withValue(Data.RAW_CONTACT_ID, local)
                    .withValues(row.toContentValues())
                    .build(),
            )
        }
        batch.add(
            ContentProviderOperation.newUpdate(RawContacts.CONTENT_URI.adapter())
                .withSelection("${RawContacts._ID}=?", arrayOf(local.toString()))
                .withValues(server(contact))
                .build(),
        )
        provider.applyBatch(batch)
    }

    override fun bind(local: Long, contact: SyncedContact, version: Long?) {
        // The dirty flag first, and only at the version the upload read: an
        // edit made meanwhile moved the version on and keeps the row dirty.
        // The server fields follow unconditionally, so a new contact always
        // learns its id and is never created twice.
        val batch = arrayListOf<ContentProviderOperation>()
        if (version != null) {
            batch.add(
                ContentProviderOperation.newUpdate(RawContacts.CONTENT_URI.adapter())
                    .withSelection(
                        "${RawContacts._ID}=? and ${RawContacts.VERSION}=?",
                        arrayOf(local.toString(), version.toString()),
                    )
                    .withValue(RawContacts.DIRTY, 0)
                    .build(),
            )
        }
        batch.add(
            ContentProviderOperation.newUpdate(RawContacts.CONTENT_URI.adapter())
                .withSelection("${RawContacts._ID}=?", arrayOf(local.toString()))
                .withValue(RawContacts.SOURCE_ID, contact.id)
                .withValue(RawContacts.SYNC1, contact.etag)
                .withValue(RawContacts.SYNC2, contact.book)
                .build(),
        )
        provider.applyBatch(batch)
    }

    override fun slug(local: Long, value: String) {
        // A write to the raw contact's own columns, not its data rows, leaves
        // VERSION where it was, so the version the upload read still holds.
        provider.update(
            RawContacts.CONTENT_URI.adapter(),
            ContentValues().apply { put(RawContacts.SYNC3, value) },
            "${RawContacts._ID}=?",
            arrayOf(local.toString()),
        )
    }

    override fun remove(local: Long) {
        provider.delete(
            RawContacts.CONTENT_URI.adapter(),
            "${RawContacts._ID}=?",
            arrayOf(local.toString()),
        )
    }

    /** The server's fields on a raw contact, and a clean dirty flag. */
    private fun server(contact: SyncedContact): ContentValues = ContentValues().apply {
        put(RawContacts.SOURCE_ID, contact.id)
        put(RawContacts.SYNC1, contact.etag)
        put(RawContacts.SYNC2, contact.book)
        put(RawContacts.DIRTY, 0)
    }

    /** The rows for a contact, a photo fitted to the provider or dropped when it will not decode. */
    private fun rows(contact: SyncedContact): List<DataRow> =
        ContactsMapping.rows(contact.properties).mapNotNull { row ->
            val blob = row.blob ?: return@mapNotNull row
            fit(blob)?.let { DataRow(row.mimetype, row.values, it) }
        }

    private fun DataRow.toContentValues(): ContentValues = ContentValues().apply {
        put(Data.MIMETYPE, mimetype)
        for ((column, value) in values) put(column, value)
        blob?.let { put(Photo.PHOTO, it) }
    }

    /**
     * A picture small enough to cross to the provider in one transaction.
     * The provider scales every photo down itself, so one past [PHOTO_MAXIMUM]
     * is shrunk here first rather than refused.
     */
    private fun fit(bytes: ByteArray): ByteArray? {
        if (bytes.size <= PHOTO_MAXIMUM) return bytes
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PHOTO_EDGE) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        bitmap.recycle()
        return out.toByteArray().takeIf { it.size <= PHOTO_MAXIMUM }
    }

    private companion object {
        /** The generic columns a managed kind may use. */
        val COLUMNS = arrayOf(
            Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4, Data.DATA5,
            Data.DATA6, Data.DATA7, Data.DATA8, Data.DATA9, Data.DATA10,
            Data.DATA11, Data.DATA12, Data.DATA13, Data.DATA14, Data.DATA15,
        )

        /** Largest photo sent to the provider whole, well inside a binder transaction. */
        const val PHOTO_MAXIMUM = 256 * 1024

        /** The edge a larger photo is scaled towards, the provider's own display size. */
        const val PHOTO_EDGE = 720
    }
}

/** The URI as the sync adapter: writes stay clean and deleted rows are visible. */
fun Uri.adapter(): Uri = buildUpon()
    .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
    .build()
