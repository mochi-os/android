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
 * Remembers which view the user last opened in each project, so reopening a
 * project lands on the board (or list) they were working in rather than on
 * whichever view happens to come first.
 *
 * SharedPreferences rather than DataStore: the value is one short id per
 * project, written only when the user picks a view, and read synchronously
 * while the project loads — an async read would let the default view render
 * first and then jump.
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
