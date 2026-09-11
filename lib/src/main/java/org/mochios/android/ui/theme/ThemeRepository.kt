// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.theme

import org.mochios.android.auth.SessionManager
import org.mochios.android.i18n.PreferencesManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caches the user's color theme locally. The theme is resolved by matching the
 * user's preference (or the server default) against the available themes, both
 * from the preferences [PreferencesManager] holds.
 */
@Singleton
class ThemeRepository @Inject internal constructor(
    private val sessionManager: SessionManager,
    private val preferencesManager: PreferencesManager,
) {

    /**
     * Cache the active theme's anchors in DataStore from the preferences the
     * last [PreferencesManager.refresh] fetched, so the theme costs no request
     * of its own. Call after that refresh; when the theme cannot be resolved
     * the cached values or defaults apply.
     */
    suspend fun cacheActiveTheme() {
        val themeId = preferencesManager.rawPreferences()["theme"]
            ?.takeIf { id -> id.isNotBlank() }
            ?: preferencesManager.defaultTheme()
            ?: return
        val theme = preferencesManager.availableThemes()
            .firstOrNull { candidate -> candidate.id == themeId }
            ?: return
        sessionManager.saveTheme(hue = theme.hue, chroma = theme.chroma, hueBg = theme.hueBg)
    }
}
