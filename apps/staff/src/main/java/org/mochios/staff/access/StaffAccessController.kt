// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.access

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.launcher.LauncherIconToggle
import org.mochios.staff.model.Me
import org.mochios.staff.repository.StaffRepository
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Toggles the Mochi Staff launcher activity (declared `android:enabled="false"` in
 * the host manifest) on the bound identity's staff role. This check is the only
 * thing that switches it off: a request refused on a staff screen shows there as
 * an error, since switching the activity off while it is open closes it.
 */
@Singleton
class StaffAccessController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val staffRepository: StaffRepository,
) {

    /**
     * Observes identity changes; [scope] must outlive any Activity (called from
     * [org.mochios.mochi.MochiApplication.onCreate]).
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            sessionManager.boundIdentity
                .distinctUntilChanged()
                .collectLatest { identity ->
                    if (identity.isNullOrBlank()) {
                        Log.i(TAG, "boundIdentity cleared — disabling staff launcher")
                        disable()
                    } else {
                        Log.i(TAG, "boundIdentity present — verifying staff access")
                        checkAccess()
                    }
                }
        }
    }

    private suspend fun checkAccess() {
        val shown = try {
            access(staffRepository.getMe())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            access(e)
        }
        Log.i(TAG, "staff access check: ${shown ?: "unchanged"}")
        if (shown != null) LauncherIconToggle.setVisible(context, LAUNCHER_CLASS_NAME, shown)
    }

    /**
     * Force the staff launcher hidden. Idempotent — re-calling when already
     * hidden is a no-op (see [LauncherIconToggle.setVisible]).
     */
    internal fun disable() {
        LauncherIconToggle.setVisible(context, LAUNCHER_CLASS_NAME, false)
    }

    companion object {
        private const val TAG = "StaffAccess"

        /**
         * Launcher activity class name from
         * `clients/android/app/src/main/AndroidManifest.xml`;
         * [LauncherIconToggle.setVisible] adds the package prefix.
         */
        const val LAUNCHER_CLASS_NAME = "MochiStaffLauncher"
    }
}

/** Whether the access check's answer shows the Staff launcher: shown for a role, hidden for none. */
internal fun access(me: Me): Boolean = me.role.isNotBlank()

/**
 * Whether a failed access check shows the Staff launcher: hidden when the
 * server refused (403); null, leaving it as it is, for anything else. A 401 is
 * the app's token not being taken rather than an answer about the role, and a
 * signed-out session switches the launcher off on its own.
 */
internal fun access(error: Exception): Boolean? = if (error is MochiError.ForbiddenError) false else null
