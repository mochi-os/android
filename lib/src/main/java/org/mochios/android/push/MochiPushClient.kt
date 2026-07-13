// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.content.Context
import android.util.Log
import org.unifiedpush.android.connector.UnifiedPush

/**
 * Per-app convenience wrapper around the UnifiedPush Android connector.
 *
 * Each Mochi app calls [register] once after auth completes. The active
 * UnifiedPush distributor on the device — whether the Mochi shell, ntfy,
 * NextPush, or anything else — issues an endpoint URL via the
 * NEW_ENDPOINT broadcast. [MochiPushReceiver] picks that up and forwards
 * it to the user's Mochi server through the notifications app's
 * `/notifications/-/push/register` action; the server stores the endpoint as an
 * `unifiedpush` account row, and from then on notifications fan out
 * through it.
 *
 * Distributor selection: if the user has multiple distributors
 * installed, [tryUseDefaultDistributor] picks the saved default (or the
 * first available). Apps can prompt the user to pick one explicitly via
 * [showDistributorPicker] when first onboarding push.
 */
object MochiPushClient {

    private const val TAG = "MochiPush"

    /**
     * Register this app to receive push via UnifiedPush. Idempotent —
     * calling twice with the same [instance] is a no-op.
     *
     * @param instance Stable per-account identifier. Use the Mochi entity
     *   ID so that re-installing the app or switching identities triggers
     *   a fresh registration.
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

    /**
     * Ensure a distributor is selected. Picks the saved default or, if
     * none, falls back to the first available distributor on device.
     * Returns true when a distributor is selected.
     */
    fun ensureDistributor(context: Context): Boolean {
        val saved = UnifiedPush.getSavedDistributor(context)
        if (!saved.isNullOrBlank()) return true
        val available = UnifiedPush.getDistributors(context)
        if (available.isEmpty()) {
            Log.w(TAG, "No UnifiedPush distributor installed")
            return false
        }
        UnifiedPush.saveDistributor(context, available.first())
        return true
    }

    /**
     * Available distributors on the device, by package name. UI can
     * present these in a picker so the user explicitly chooses (e.g.
     * during first-run onboarding for push notifications).
     */
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
