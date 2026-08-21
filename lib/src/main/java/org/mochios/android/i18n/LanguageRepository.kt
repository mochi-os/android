// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.i18n

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.mochios.android.auth.SessionManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the user's `language` preference for the next launch; apply it now
 * with [LocaleHelper.apply] and `Activity.recreate()`.
 */
@Singleton
class LanguageRepository @Inject internal constructor(
    @param:ApplicationContext private val context: Context,
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
