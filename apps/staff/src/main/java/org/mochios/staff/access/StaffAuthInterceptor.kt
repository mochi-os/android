// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.access

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Hides the launcher icon when a staff API call returns 401 or 403. Reaches the
 * controller through [StaffAccessController.instanceRef], so it needs no Hilt
 * graph at construction.
 */
class StaffAuthInterceptor : Interceptor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code == 401 || response.code == 403) {
            val controller = StaffAccessController.instanceRef?.get()
            if (controller != null) {
                Log.i(TAG, "Staff API returned ${response.code} on ${chain.request().url} — disabling staff launcher")
                scope.launch { controller.disable() }
            }
        }
        return response
    }

    companion object {
        private const val TAG = "StaffAuthInterceptor"
    }
}
