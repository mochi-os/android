// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.lib

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Board columns collapsed per CRM, by the field the board's columns come
 * from, as the web keeps them: two views that group by the same field share
 * them. Kept on this device only.
 */
@Singleton
class CollapsedColumnsStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The collapsed column ids of each column field in [crmId]. */
    fun get(crmId: String): Map<String, Set<String>> {
        val prefix = "$crmId/"
        return prefs.all.mapNotNull { (key, value) ->
            if (!key.startsWith(prefix) || value !is Set<*>) return@mapNotNull null
            key.removePrefix(prefix) to value.filterIsInstance<String>().toSet()
        }.toMap()
    }

    /** Collapses [column] of [field] in [crmId] if it is open, else opens it; returns the field's collapsed set. */
    fun toggle(crmId: String, field: String, column: String): Set<String> {
        val key = "$crmId/$field"
        val current = prefs.getStringSet(key, null).orEmpty()
        val next = if (column in current) current - column else current + column
        if (crmId.isBlank() || field.isBlank()) return next
        prefs.edit {
            if (next.isEmpty()) remove(key) else putStringSet(key, next)
        }
        return next
    }

    private companion object {
        const val PREFS = "mochi_crm_collapsed_columns"
    }
}
