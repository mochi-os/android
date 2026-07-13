// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.content.Context

/**
 * Persists the most recently opened item id per feature so the next launch
 * lands the user where they left off instead of on a list screen. Used by
 * the per-feature nav-graph router composable to pick its start destination.
 *
 * Each feature owns its own key — the value is opaque (could be a fingerprint,
 * an entity uid, or the special "__all__" pseudo-id where the feature has an
 * all-items aggregate view).
 *
 * SharedPreferences is fine here: the values are tiny opaque strings, written
 * on detail-screen entry (not on every scroll / interaction), and reads are
 * synchronous which avoids a recomposition-blocking suspend on cold start.
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
