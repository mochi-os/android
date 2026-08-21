// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.lib

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

/**
 * Last-opened view per project. SharedPreferences, not DataStore: the read must
 * be synchronous while the project loads, or the default view renders first and
 * then jumps.
 */
@Singleton
class ActiveViewStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** View id last opened in [projectId], or null if the user never picked one. */
    fun get(projectId: String): String? =
        prefs.getString(projectId, null)?.takeIf { id -> id.isNotBlank() }

    fun set(projectId: String, viewId: String) {
        if (projectId.isBlank() || viewId.isBlank()) return
        prefs.edit { putString(projectId, viewId) }
    }

    private companion object {
        const val PREFS = "mochi_projects_active_view"
    }
}
