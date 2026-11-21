// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * Persists the most recently opened item id per feature, so the next launch
 * lands there instead of on a list screen. Values are opaque; [ALL] is the
 * aggregate view.
 */
object LastViewedStore {

    private const val PREFS = "mochi_last_viewed"

    /** Special id meaning the feature's all-items aggregate view. */
    const val ALL = "__all__"

    /** The id each feature's router reopened at launch, until its screen asks [reopened]. */
    private val reopening = ConcurrentHashMap<String, String>()

    fun get(context: Context, feature: String): String? =
        prefs(context).getString(feature, null)?.takeIf { it.isNotBlank() }

    /**
     * The item a router opens at launch: the one last viewed, or [ALL]. Its
     * screen learns from [reopened] that nobody asked for it this time.
     */
    fun restore(context: Context, feature: String): String {
        val id = get(context, feature) ?: return ALL
        reopening[feature] = id
        return id
    }

    /**
     * Whether [id] is the item [restore] reopened, rather than one opened from
     * a link, a notification or the drawer. Answers once: a later opening of
     * the same item was asked for.
     */
    fun reopened(feature: String, id: String): Boolean = reopening.remove(feature, id)

    /** Forget [id], gone from the server, if it is the one stored. */
    fun drop(context: Context, feature: String, id: String) {
        if (get(context, feature) == id) clear(context, feature)
    }

    fun set(context: Context, feature: String, id: String) {
        if (id.isBlank()) return
        prefs(context).edit().putString(feature, id).apply()
    }

    fun clear(context: Context, feature: String) {
        prefs(context).edit().remove(feature).apply()
    }

    /**
     * Forget every feature's item. They are the signed-in account's, on its
     * server: another one would be sent to a feed or forum it cannot find.
     */
    fun forget(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
