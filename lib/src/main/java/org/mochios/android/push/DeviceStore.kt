// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The id this phone asserts to the server as its device, in the `Device`
 * header. Minted once and kept for the life of the install: the app's stores
 * are excluded from backup, so a reinstall is a new device, the same boundary a
 * session has. Deliberately not [PushAccountStore]: that holds the push account
 * id, which churns with every transport switch or reissued endpoint, and this
 * record exists to outlive it.
 */
@Singleton
class DeviceStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val prefs by lazy {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
    }

    /** The device id, minted on first use. */
    fun id(): String = synchronized(this) {
        prefs.getString(KEY_ID, null)?.takeIf { deviceIdValid(it) }
            ?: mintDeviceId().also { prefs.edit().putString(KEY_ID, it).apply() }
    }

    private companion object {
        const val PREF_FILE = "mochi_device"
        const val KEY_ID = "id"
    }
}

/** A fresh device id: a random UUID, which is within [deviceIdValid]. */
internal fun mintDeviceId(): String = UUID.randomUUID().toString()

/**
 * The shape the server holds a device id to (core's device_pattern): 8 to 64
 * characters of ASCII letters, digits and hyphens.
 */
internal fun deviceIdValid(id: String): Boolean =
    id.length in 8..64 && id.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' }
