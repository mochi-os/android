// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local record of the push account id the server minted for this device - the
 * `accounts.id` uid `/notifications/-/accounts/remove` takes, not the identity.
 * Keyed by UnifiedPush instance (the bound identity), so two identities keep
 * separate ids.
 */
@Singleton
class PushAccountStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val prefs by lazy {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
    }

    /** Remember the account id the server returned for [instance]. */
    fun store(instance: String, accountId: String) {
        prefs.edit()
            .putString(key(instance), accountId)
            .apply()
    }

    /** The account id registered for [instance], or null if we never got one. */
    fun read(instance: String): String? {
        // Tolerate the legacy integer-typed value written by builds that
        // predate the string-uid account id: getString throws if the stored
        // entry is still an Int, so fall back to reading it as an Int.
        return try {
            prefs.getString(key(instance), null)?.takeIf { id -> id.isNotBlank() }
        } catch (_: ClassCastException) {
            prefs.getInt(key(instance), -1).takeIf { id -> id > 0 }?.toString()
        }
    }

    /** Forget [instance]'s account id after the server has dropped it. */
    fun clear(instance: String) {
        prefs.edit()
            .remove(key(instance))
            .apply()
    }

    private fun key(instance: String) = "push_account_id:$instance"

    private companion object {
        const val PREF_FILE = "mochi_push"
    }
}
