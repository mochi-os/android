// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.theme

import org.mochios.android.auth.SessionManager
import org.mochios.android.auth.TokenApi
import org.mochios.android.auth.TokenRequest
import org.mochios.android.i18n.PreferencesApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches the user's color theme from the settings app and caches it locally.
 * The theme is resolved by matching the user's preference (or system default)
 * against the available themes list.
 */
@Singleton
class ThemeRepository @Inject internal constructor(
    private val sessionManager: SessionManager,
    private val tokenApi: TokenApi,
    private val api: PreferencesApi,
) {

    /**
     * Fetch the user's theme from the server and cache it in DataStore.
     * Call after authentication is complete.
     */
    suspend fun fetchAndCacheTheme() {
        try {
            // Get a settings app token
            val tokenResponse = tokenApi.fetchToken(TokenRequest("settings"))
            val tokenBody = tokenResponse.body() ?: return
            val jwt = tokenBody.token

            // Fetch preferences with the settings token
            val response = api.getPreferences("Bearer $jwt")
            val data = response.body() ?: return

            // Resolve which theme is active
            val themeId = data.preferences?.get("theme")
                ?: data.default_theme
                ?: return
            val theme = data.themes?.find { it.id == themeId } ?: return
            val hue = theme.hue?.toFloat() ?: return
            val chroma = theme.chroma?.toFloat() ?: return
            val hueBg = theme.hue_bg?.toFloat() ?: return

            sessionManager.saveTheme(hue, chroma, hueBg)
        } catch (_: Exception) {
            // Theme fetch is best-effort — cached values or defaults apply
        }
    }
}
