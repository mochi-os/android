// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.content.Context

/**
 * Persists the most recently opened item id per feature, so the next launch
 * lands there instead of on a list screen. Values are opaque; [ALL] is the
 * aggregate view.
 */
object LastViewedStore {

    private const val PREFS = "mochi_last_viewed"

    /** Special id meaning the feature's all-items aggregate view. */
    const val ALL = "__all__"

    fun get(context: Context, feature: String): String? =
        prefs(context).getString(feature, null)?.takeIf { it.isNotBlank() }

    fun set(context: Context, feature: String, id: String) {
        if (id.isBlank()) return
        prefs(context).edit().putString(feature, id).apply()
    }

    fun clear(context: Context, feature: String) {
        prefs(context).edit().remove(feature).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
