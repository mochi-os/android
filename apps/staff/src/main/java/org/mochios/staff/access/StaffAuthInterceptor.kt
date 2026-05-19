package org.mochios.staff.access

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp [Interceptor] that watches every staff API response for 401 / 403
 * and hides the launcher icon when one fires.
 *
 * Hooked into the per-app OkHttp builder in
 * [org.mochios.staff.di.AppModule] AFTER the Bearer interceptor — the
 * Bearer one attaches the staff JWT on the way out and this one inspects
 * the resulting status on the way back. Adding them in reverse order is a
 * common mistake; OkHttp invokes application interceptors in declaration
 * order on the request and in reverse on the response, so the Bearer
 * attachment must come first so its `chain.proceed` returns a response
 * with the right status for us to read.
 *
 * The disable call dispatches off the OkHttp thread via a SupervisorScope
 * so a launcher-toggle exception can't tear down an unrelated request that
 * happens to share the dispatcher. The controller reference is held via a
 * WeakReference on [StaffAccessController.instanceRef] so this file is
 * safe to instantiate before the Hilt graph is fully wired (e.g. during
 * cold-start before the first `boundIdentity` collect kicks off).
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
