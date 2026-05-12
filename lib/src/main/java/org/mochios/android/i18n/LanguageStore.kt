package org.mochios.android.i18n

import android.content.Context

/**
 * Synchronous, boot-safe storage for the user's `language` preference.
 *
 * Lives in SharedPreferences (not DataStore) because `attachBaseContext` —
 * where the locale needs to be applied — runs before Hilt graph construction
 * and before any coroutine machinery is available. SharedPreferences can be
 * read synchronously from any Context.
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
