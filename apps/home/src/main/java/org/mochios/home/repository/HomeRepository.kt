// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.home.repository

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonParseException
import dagger.hilt.android.qualifiers.ApplicationContext
import org.mochios.android.api.unwrapRaw
import org.mochios.android.auth.SessionManager
import org.mochios.android.launcher.launchable
import org.mochios.android.util.NaturalCompare
import org.mochios.home.api.HomeApi
import org.mochios.home.api.IconsResponse
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One app on the home grid: [name] is the app as the client knows it, which
 * is the path the server serves it at; [label] is its name in the user's
 * language; [highlight] asks for attention.
 */
data class Tile(
    val name: String,
    val label: String,
    val highlight: Boolean = false,
)

/**
 * What the home grid shows: [tiles] in order, and the user's theme's tile
 * [mask] and [background], null for the plain look.
 */
data class Grid(
    val tiles: List<Tile>,
    val mask: String? = null,
    val background: String? = null,
)

/**
 * The grid for the server's answer: only the apps this client can open, as
 * the server has already dropped those the user may not see, sorted by label
 * as the web sorts them. An app listed twice keeps its first entry.
 */
fun grid(response: IconsResponse): Grid {
    val tiles = response.icons
        .filter { icon -> icon.link.isNotBlank() && icon.name.isNotBlank() && launchable(icon.link) }
        .distinctBy { icon -> icon.link.lowercase() }
        .map { icon -> Tile(name = icon.link.lowercase(), label = icon.name, highlight = icon.highlight) }
        .sortedWith(compareBy(NaturalCompare) { tile -> tile.label })
    return Grid(
        tiles = tiles,
        mask = response.mask?.takeIf { mask -> mask.isNotBlank() },
        background = response.background?.takeIf { background -> background.isNotBlank() },
    )
}

/**
 * The server's last answer for each account on this device, so the grid
 * paints at once, and offline. Kept per account so signing in as someone
 * else never shows the previous account's apps.
 */
@Singleton
class HomeCache @Inject constructor(
    @ApplicationContext context: Context,
    private val gson: Gson,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The answer last kept for [account], or null when there is none or it no longer reads. */
    fun read(account: String): IconsResponse? {
        val json = prefs.getString(account, null) ?: return null
        return try {
            gson.fromJson(json, IconsResponse::class.java)
        } catch (_: JsonParseException) {
            null
        }
    }

    /** Keeps [response] as [account]'s last answer. */
    fun write(account: String, response: IconsResponse) {
        prefs.edit { putString(account, gson.toJson(response)) }
    }

    private companion object {
        const val PREFS = "mochi_home"
    }
}

/** The home grid, from the server and from what was last kept for the account. */
@Singleton
class HomeRepository @Inject constructor(
    private val api: HomeApi,
    private val cache: HomeCache,
    private val sessionManager: SessionManager,
) {

    /** The grid as last fetched for the signed-in account, or null when there is none. */
    suspend fun cached(): Grid? {
        val account = sessionManager.getBoundIdentity() ?: return null
        return cache.read(account)?.let(::grid)
    }

    /** Fetches the grid from the server and keeps the answer for the signed-in account. */
    suspend fun fetch(): Grid {
        val response = api.icons().unwrapRaw()
        sessionManager.getBoundIdentity()?.let { account -> cache.write(account, response) }
        return grid(response)
    }
}
