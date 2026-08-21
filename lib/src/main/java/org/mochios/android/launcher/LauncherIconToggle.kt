// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * Show or hide a launcher activity-alias at runtime. An alias gated on a
 * server-side capability check ships `android:enabled="false"` and is enabled
 * here once it passes. Android owns the state across reboots, so apps re-verify
 * on each boot.
 */
object LauncherIconToggle {

    private const val TAG = "LauncherIconToggle"

    /**
     * [aliasClassName] is the simple class name (e.g. `"MochiStaffLauncher"`);
     * the host's package prefix is added. No-ops when already in the target
     * state.
     */
    fun setVisible(context: Context, aliasClassName: String, visible: Boolean) {
        val component = ComponentName(context, "${context.packageName}.$aliasClassName")
        val pm = context.packageManager
        val targetState = if (visible) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        val currentState = try {
            pm.getComponentEnabledSetting(component)
        } catch (e: IllegalArgumentException) {
            // Component not declared in the manifest — caller passed a bogus
            // alias name. Log and bail rather than fall through to a write
            // that would also throw.
            Log.w(TAG, "Unknown launcher alias: ${component.flattenToShortString()}", e)
            return
        }
        // Treat DEFAULT (i.e. follow whatever android:enabled says in the
        // manifest) as the corresponding boolean for comparison. Manifest's
        // android:enabled="false" → DEFAULT means "currently disabled".
        val effectivelyEnabled = when (currentState) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            else -> {
                // DEFAULT / DISABLED_USER / DISABLED_UNTIL_USED. Compare against
                // the target so we still emit the set call when DEFAULT
                // disagrees with the target.
                !visible
            }
        }
        if (effectivelyEnabled == visible && currentState != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
            return
        }
        Log.i(
            TAG,
            "setComponentEnabledSetting ${component.flattenToShortString()} " +
                "${if (visible) "ENABLED" else "DISABLED"} (was state=$currentState)",
        )
        pm.setComponentEnabledSetting(component, targetState, PackageManager.DONT_KILL_APP)
    }
}
