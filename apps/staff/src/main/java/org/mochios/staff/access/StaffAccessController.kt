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
import org.mochios.staff.repository.StaffRepository
import android.util.Log
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Toggles the Mochi Staff launcher alias (declared `android:enabled="false"` in
 * the host manifest) on the bound identity's staff role. Only a 401/403 or a
 * blank role disables it; network errors leave the current state untouched.
 */
@Singleton
class StaffAccessController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val staffRepository: StaffRepository,
) {

    init {
        // Publish a weak self-reference so the OkHttp interceptor (which runs
        // off the controller scope and cannot inject the Hilt graph itself)
        // can call back into disable() on 401/403 without holding a strong
        // ref that would defeat process-death cleanup.
        instanceRef = WeakReference(this)
    }

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
        val me = try {
            staffRepository.getMe()
        } catch (e: MochiError.AuthError) {
            Log.i(TAG, "getMe() rejected (401) — disabling staff launcher")
            disable()
            return
        } catch (e: MochiError.ForbiddenError) {
            Log.i(TAG, "getMe() rejected (403) — disabling staff launcher")
            disable()
            return
        } catch (e: MochiError) {
            Log.i(TAG, "getMe() failed transiently (${e.javaClass.simpleName}); leaving icon state untouched")
            return
        } catch (e: Exception) {
            // Defensive: anything escaping the typed MochiError funnel is
            // treated as transient (don't strip the icon on an unrelated
            // crash either).
            Log.w(TAG, "getMe() threw unexpectedly; leaving icon state untouched", e)
            return
        }
        if (me.role.isBlank()) {
            Log.i(TAG, "getMe() returned blank role for ${me.id.take(8)}… — disabling staff launcher")
            disable()
        } else {
            Log.i(TAG, "getMe() returned role=${me.role} for ${me.id.take(8)}… — enabling staff launcher")
            LauncherIconToggle.setVisible(context, ALIAS_CLASS_NAME, true)
        }
    }

    /**
     * Force the staff launcher hidden. Idempotent — re-calling when already
     * hidden is a no-op (see [LauncherIconToggle.setVisible]).
     */
    internal fun disable() {
        LauncherIconToggle.setVisible(context, ALIAS_CLASS_NAME, false)
    }

    companion object {
        private const val TAG = "StaffAccess"

        /**
         * Activity-alias name from
         * `clients/android/app/src/main/AndroidManifest.xml`;
         * [LauncherIconToggle.setVisible] adds the package prefix.
         */
        const val ALIAS_CLASS_NAME = "MochiStaffLauncher"

        // Back-channel for [StaffAuthInterceptor]: injecting this singleton
        // into the OkHttp client would make a Hilt provision cycle.
        @Volatile
        internal var instanceRef: WeakReference<StaffAccessController>? = null
    }
}
