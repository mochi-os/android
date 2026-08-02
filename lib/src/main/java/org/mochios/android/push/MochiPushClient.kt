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
 * Distributor selection: [ensureDistributor] keeps a saved choice if there is
 * one, and otherwise prefers this app's own distributor over any third party.
 * That preference matters because the Mochi shell ships a distributor itself,
 * and the connector returns installed distributors in PackageManager order —
 * so taking the first would silently route a self-hosted user's notifications
 * through ntfy or NextPush if either happened to be installed.
 *
 * There is deliberately no picker yet. This KDoc used to promise
 * `tryUseDefaultDistributor` and `showDistributorPicker`, neither of which
 * exists; `availableDistributors` and `selectDistributor` are present but have
 * no callers, so a user who wants a third-party distributor has to select it
 * through that API from an app that calls it.
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
     * Ensure a distributor is selected: keeps the saved choice when there is
     * one, otherwise adopts this app's own distributor in preference to any
     * third party (see [preferOwnDistributor]). Returns true when a
     * distributor is selected.
     */
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

/**
 * Which distributor to adopt when the user has not chosen one.
 *
 * Prefers [own]. The connector lists installed distributors in PackageManager
 * order with no preference for the caller, and the Mochi shell registers a
 * distributor itself, so taking the first entry would hand a self-hosted user's
 * notifications to whichever third-party distributor happened to sort earlier.
 */
internal fun preferOwnDistributor(available: List<String>, own: String): String =
    available.firstOrNull { it == own } ?: available.first()

