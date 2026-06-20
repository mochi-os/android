// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONObject

/**
 * Persistent registry of UnifiedPush subscriptions issued by this distributor.
 *
 * Each entry maps a per-App `token` (chosen by the App; usually a stable
 * identifier like the user's Mochi entity ID) to:
 *   - `appPackage`  — Application's package name; used to dispatch MESSAGE
 *                     intents back to the right App.
 *   - `subId`       — opaque server-allocated subscription id; appears in
 *                     the endpoint URL path as `/notifications/-/push/inbound/<subId>`.
 *   - `server`      — base URL of the Mochi server that issued the subId.
 *                     Lets us POST registration/unregistration from the
 *                     correct identity context.
 *   - `auth`/`p256dh` — Web Push subscription keys (base64url) generated on
 *                     the device; the matching p256dh private key is held
 *                     by the connector library for envelope decryption.
 *
 * Stored in a single SharedPreferences file `mochi_distributor`. Single-user
 * IPC is fine — the distributor service runs in the shell app's process.
 */
class DistributorStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    data class Entry(
        val token: String,
        val appPackage: String,
        val subId: String,
        val server: String,
        val auth: String,
        val p256dh: String,
    )

    fun put(entry: Entry) {
        prefs.edit().putString(keyFor(entry.token), entryToJson(entry).toString()).apply()
        Log.i(TAG, "stored token=${entry.token} pkg=${entry.appPackage} sub=${entry.subId}")
    }

    fun get(token: String): Entry? {
        val json = prefs.getString(keyFor(token), null) ?: return null
        return runCatching { entryFromJson(JSONObject(json)) }.getOrNull()
    }

    fun bySubId(subId: String): Entry? = all().firstOrNull { it.subId == subId }

    fun all(): List<Entry> {
        val out = mutableListOf<Entry>()
        for ((k, v) in prefs.all) {
            if (!k.startsWith(KEY_PREFIX) || v !is String) continue
            runCatching { out += entryFromJson(JSONObject(v)) }
        }
        return out
    }

    fun remove(token: String) {
        prefs.edit().remove(keyFor(token)).apply()
        Log.i(TAG, "removed token=$token")
    }

    private fun keyFor(token: String) = KEY_PREFIX + token

    private fun entryToJson(e: Entry) = JSONObject().apply {
        put("token", e.token)
        put("appPackage", e.appPackage)
        put("subId", e.subId)
        put("server", e.server)
        put("auth", e.auth)
        put("p256dh", e.p256dh)
    }

    private fun entryFromJson(o: JSONObject) = Entry(
        token = o.getString("token"),
        appPackage = o.getString("appPackage"),
        subId = o.getString("subId"),
        server = o.getString("server"),
        auth = o.getString("auth"),
        p256dh = o.getString("p256dh"),
    )

    private companion object {
        const val PREF_FILE = "mochi_distributor"
        const val KEY_PREFIX = "subscription:"
        const val TAG = "MochiDistributor"
    }
}
