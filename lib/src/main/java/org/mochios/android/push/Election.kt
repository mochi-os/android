// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.content.Context
import android.content.pm.PackageManager

/**
 * Cross-app leader election for which Mochi-signed app on the device
 * hosts the UnifiedPush distributor. See claude/plans/android-notifications.md
 * for the full architecture.
 *
 * Election rules:
 *  1. If `org.mochios.mochi` (the Mochi shell) is installed → it wins.
 *  2. Else, alphabetical-first installed Mochi package wins.
 *  3. Re-election when the leader is uninstalled / force-stopped /
 *     voluntarily hands over.
 *
 * State is persisted via [MochiAccount] user-data on a sentinel account
 * (cross-app, signature-protected). Implementation is deferred —
 * skeleton only for now.
 */
object Election {

    /** Well-known package name of the Mochi shell. Always wins when installed. */
    const val SHELL_PACKAGE = "org.mochios.mochi"

    /** Package names of all per-app Mochi apps that can act as fallback leaders. */
    val FALLBACK_PACKAGES = listOf(
        "org.mochios.chat",
        "org.mochios.feeds",
        "org.mochios.forums",
        "org.mochios.projects",
    )

    /**
     * Returns the package name of the app that should host the
     * distributor on this device, based on what's installed.
     */
    fun electedLeader(context: Context): String? {
        val pm = context.packageManager
        if (isInstalled(pm, SHELL_PACKAGE)) return SHELL_PACKAGE
        val installedFallbacks = FALLBACK_PACKAGES.filter { isInstalled(pm, it) }.sorted()
        return installedFallbacks.firstOrNull()
    }

    /**
     * True iff the *running* app is the one that should host the
     * distributor. Used by [PushService] to decide whether to start
     * itself; non-leader apps see false and yield.
     */
    fun isLeader(context: Context): Boolean {
        val elected = electedLeader(context) ?: return false
        return elected == context.packageName
    }

    private fun isInstalled(pm: PackageManager, pkg: String): Boolean {
        return try {
            pm.getApplicationInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    // TODO: persist epoch + leader in MochiAccount user-data so peer apps can
    // observe the change via accountsFlow and re-register. See plan §
    // "Election rules" point 5 (on leader change broadcast a reregister
    // request to all peer Mochi apps).
}
