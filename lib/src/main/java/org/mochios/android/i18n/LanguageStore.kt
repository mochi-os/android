// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.i18n

import android.content.Context

/**
 * Synchronous storage for the user's `language`. SharedPreferences, not
 * DataStore: `attachBaseContext` runs before Hilt and any coroutine machinery.
 */
object LanguageStore {

    private const val PREFS = "mochi_language"
    private const val KEY = "tag"

    fun get(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun set(context: Context, tag: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (tag.isNullOrBlank()) remove(KEY) else putString(KEY, tag)
            apply()
        }
    }
}
