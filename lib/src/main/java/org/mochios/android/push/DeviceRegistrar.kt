// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.content.Context
import android.util.Log
import dagger.hilt.android.EntryPointAccessors
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Registers this device with the server, or refreshes its name: the server
 * keeps one row per device id, and the push accounts registered from the
 * device hang off it. Runs before either push transport registers, so the push
 * account binds to a device the server already knows.
 */
object DeviceRegistrar {

    private const val TAG = "MochiDevice"

    /** True when the server accepted the registration. Failure is logged only:
     *  push registration goes ahead unbound and the next configure heals it. */
    suspend fun register(context: Context, client: OkHttpClient, server: String): Boolean {
        val deps = EntryPointAccessors
            .fromApplication(context.applicationContext, PushEntryPoint::class.java)
        val token = deps.authRepository().fetchToken("notifications").getOrNull()
        if (token == null) {
            Log.w(TAG, "register: could not mint notifications app token")
            return false
        }
        val form = FormBody.Builder()
            .add("label", DeviceName.resolve(context))
            .build()
        val request = Request.Builder()
            .url(server.trimEnd('/') + "/notifications/-/device/register")
            .header("Authorization", "Bearer $token")
            .header("Device", deps.deviceStore().id())
            .post(form)
            .build()
        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) Log.w(TAG, "/notifications/-/device/register returned ${resp.code}")
                resp.isSuccessful
            }
        } catch (e: Exception) {
            Log.w(TAG, "register failed: ${e.message}")
            false
        }
    }
}
