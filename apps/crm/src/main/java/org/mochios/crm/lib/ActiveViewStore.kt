// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.lib

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

/**
 * Last-opened view per CRM. SharedPreferences, not DataStore: the value is read
 * synchronously while the CRM loads, so the default view never renders first
 * and then jumps.
 */
@Singleton
class ActiveViewStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** View id last opened in [crmId], or null if the user never picked one. */
    fun get(crmId: String): String? =
        prefs.getString(crmId, null)?.takeIf { id -> id.isNotBlank() }

    fun set(crmId: String, viewId: String) {
        if (crmId.isBlank() || viewId.isBlank()) return
        prefs.edit { putString(crmId, viewId) }
    }

    private companion object {
        const val PREFS = "mochi_crm_active_view"
    }
}
