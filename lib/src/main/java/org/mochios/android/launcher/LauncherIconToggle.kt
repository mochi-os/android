// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * Show or hide a launcher activity-alias at runtime.
 *
 * Each Mochi-app declares a `<activity-alias>` in the host's manifest
 * carrying a `MAIN` / `LAUNCHER` intent filter; the OS treats each one as a
 * separate entry in the launcher. Most aliases ship with
 * `android:enabled="true"` and stay that way. Apps whose icon should only
 * appear after a server-side capability check (staff today, paid-feature
 * unlocks tomorrow) declare `android:enabled="false"` and call this object
 * once the check passes.
 *
 * The COMPONENT_ENABLED_STATE setting persists across reboots: state is
 * owned by Android, not by app SharedPreferences. Apps still re-verify on
 * every boot and correct the previous state if the server-side role has
 * changed.
 *
 * `DONT_KILL_APP` keeps the running process alive while the launcher
 * refreshes — without it, Android may terminate the host whenever an alias
 * is toggled, which would tear down anything from a notification handoff
 * to a half-typed reply.
 */
object LauncherIconToggle {

    private const val TAG = "LauncherIconToggle"

    /**
     * Flip the given activity-alias to the target visibility.
     *
     * `aliasClassName` is the simple class name (e.g. `"MochiStaffLauncher"`);
     * the host's package prefix is added automatically. Reads the current
     * state first and no-ops when already in the target state, so repeated
     * calls during a cold-start `getMe()` round-trip don't spuriously notify
     * the launcher every time.
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
