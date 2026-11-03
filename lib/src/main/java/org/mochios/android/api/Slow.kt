// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.api

import okhttp3.Interceptor
import retrofit2.Invocation
import java.util.concurrent.TimeUnit

/**
 * Marks a call whose server action works longer than a request is usually
 * given and always answers: an import within its budget, a community joined
 * or a chat made through an account on another server. [slowInterceptor] lets
 * it wait as long as the server may run an action, where the client's usual
 * 30 seconds reported a failure for work that went on to finish.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Slow

/** The longest the server runs an action (its 300 seconds), with a margin. */
internal const val SLOW_SECONDS = 330

/** Waits [SLOW_SECONDS] for the answer to a call marked [Slow]. */
internal val slowInterceptor = Interceptor { chain ->
    val slow = chain.request().tag(Invocation::class.java)?.method()?.isAnnotationPresent(Slow::class.java) == true
    if (slow) {
        chain.withReadTimeout(SLOW_SECONDS, TimeUnit.SECONDS).proceed(chain.request())
    } else {
        chain.proceed(chain.request())
    }
}
