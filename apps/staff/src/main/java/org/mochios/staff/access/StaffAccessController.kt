// Copyright © 2026 Mochi OÜ
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
 * Drives the visibility of the Mochi Staff launcher alias.
 *
 * The staff app is the operator console. Its launcher entry is declared with
 * `android:enabled="false"` in the host's `AndroidManifest.xml`, so a fresh
 * install hides it from the launcher by default. This controller watches the
 * bound identity, and:
 *
 *  - On sign-in (identity transitions from null to a value): calls
 *    `staffApi.me()`. If the response carries a non-blank `role`, the alias
 *    is enabled and "Mochi Staff" appears in the launcher. If the response
 *    is a 401/403, or a 2xx with a blank role, the alias is disabled.
 *  - On sign-out (identity becomes null): disables the alias.
 *  - On a 401 / 403 from any staff endpoint mid-session (account suspended,
 *    role revoked, JWT invalidated): disables the alias via the
 *    [StaffAuthInterceptor] back-channel.
 *
 * Network errors are deliberately non-decisive — a flaky connection at
 * launch must not strip a working operator's icon. Only an explicit "you
 * are no longer staff" response from the server flips the alias off.
 */
@Singleton
class StaffAccessController @Inject constructor(
    @ApplicationContext private val context: Context,
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
     * Start observing identity changes. Called from
     * [org.mochios.mochi.MochiApplication.onCreate] with a long-lived scope
     * so the observation outlives any single Activity.
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

    /**
     * Ask the server whether the bound identity is a staff member.
     *
     *   - 2xx with a non-blank role  → enable the launcher.
     *   - 2xx with a blank role       → identity is signed in but not staff;
     *                                   disable.
     *   - 401 / 403                   → identity rejected by the staff app;
     *                                   disable.
     *   - any other error (network,
     *     server 5xx, parse failure)  → leave the current state untouched.
     *                                   A flaky connection on cold start
     *                                   must not strip an operator's icon.
     */
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
         * Simple class name of the activity-alias declared in
         * `clients/android/app/src/main/AndroidManifest.xml`. The host
         * package prefix is added by [LauncherIconToggle.setVisible].
         */
        const val ALIAS_CLASS_NAME = "MochiStaffLauncher"

        // Off-graph back-channel for OkHttp's interceptor thread. The
        // interceptor lives in a Retrofit-managed OkHttpClient and can't
        // hold a @Inject reference to the singleton itself without
        // creating a recursive provision cycle (the OkHttpClient is
        // injected into the Retrofit which produces the StaffApi which
        // backs the StaffRepository which backs this controller). The
        // WeakReference + companion is the minimum that avoids the cycle.
        @Volatile
        internal var instanceRef: WeakReference<StaffAccessController>? = null
    }
}
