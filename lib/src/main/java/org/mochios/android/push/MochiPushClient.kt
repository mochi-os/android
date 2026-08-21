// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.content.Context
import android.util.Log
import org.unifiedpush.android.connector.UnifiedPush

/**
 * Per-app wrapper around the UnifiedPush connector. Each app calls [register]
 * once after auth; [MochiPushReceiver] forwards the endpoint to the server's
 * `/notifications/-/push/register`, which stores it as a `unifiedpush` account.
 */
object MochiPushClient {

    private const val TAG = "MochiPush"

    /**
     * Register this app for UnifiedPush. [instance] must be a stable
     * per-account id - use the Mochi entity id. Idempotent for the same
     * instance.
     */
    fun register(context: Context, instance: String) {
        if (instance.isBlank()) {
            Log.w(TAG, "register called with blank instance — skipping")
            return
        }
        ensureDistributor(context)
        try {
            UnifiedPush.register(context, instance)
        } catch (e: Exception) {
            Log.w(TAG, "registerApp failed: ${e.message}")
        }
    }

    /** Tear down the subscription on logout / account removal. */
    fun unregister(context: Context, instance: String) {
        try {
            UnifiedPush.unregister(context, instance)
        } catch (e: Exception) {
            Log.w(TAG, "unregisterApp failed: ${e.message}")
        }
    }

    fun ensureDistributor(context: Context): Boolean {
        val saved = UnifiedPush.getSavedDistributor(context)
        if (!saved.isNullOrBlank()) return true
        val available = UnifiedPush.getDistributors(context)
        if (available.isEmpty()) {
            Log.w(TAG, "No UnifiedPush distributor installed")
            return false
        }
        val chosen = preferOwnDistributor(available, context.packageName)
        UnifiedPush.saveDistributor(context, chosen)
        Log.i(TAG, "Selected UnifiedPush distributor $chosen of ${available.size}")
        return true
    }

    fun availableDistributors(context: Context): List<String> =
        UnifiedPush.getDistributors(context)

    /** Currently selected distributor, or null if none chosen yet. */
    fun selectedDistributor(context: Context): String? =
        UnifiedPush.getSavedDistributor(context)

    /**
     * Persist an explicit distributor choice (from a picker UI) and
     * register all installed Mochi apps against it.
     */
    fun selectDistributor(context: Context, packageName: String, instances: List<String>) {
        UnifiedPush.saveDistributor(context, packageName)
        for (instance in instances) {
            try {
                UnifiedPush.register(context, instance)
            } catch (e: Exception) {
                Log.w(TAG, "registerApp($instance) failed: ${e.message}")
            }
        }
    }
}

/**
 * Which distributor to adopt when the user has not chosen one. The connector
 * lists them in PackageManager order, so without preferring [own] a self-hosted
 * user's push could go to ntfy or NextPush.
 */
internal fun preferOwnDistributor(available: List<String>, own: String): String =
    available.firstOrNull { it == own } ?: available.first()

