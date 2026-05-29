package org.mochios.android.i18n

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.mochios.android.auth.SessionManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches the user's `language` preference from the settings app and stores
 * it in [LanguageStore] for the next process launch. Apply the change in the
 * current process by calling [LocaleHelper.apply] and `Activity.recreate()`.
 */
@Singleton
class LanguageRepository @Inject internal constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val api: PreferencesApi,
) {
    suspend fun fetchAndStore(): String? {
        val token = sessionManager.getToken("settings") ?: return null
        return try {
            val resp = api.getPreferences("Bearer $token")
            val tag = resp.body()?.preferences?.get("language") ?: return null
            LanguageStore.set(context, tag)
            tag
        } catch (_: Exception) {
            null
        }
    }
}
