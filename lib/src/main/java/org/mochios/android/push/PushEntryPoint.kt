// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import org.mochios.android.auth.AuthRepository
import org.mochios.android.auth.SessionManager

/**
 * Shared Hilt entry point for the push package. Its components are instantiated
 * by the Android framework ([MochiPushReceiver], [MochiFirebaseMessagingService])
 * or are DI-less objects ([FcmRegistrar], [PushTransport]), so they reach the
 * graph via [dagger.hilt.android.EntryPointAccessors] rather than constructor
 * injection. Callers use only the accessors they need.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PushEntryPoint {
    fun sessionManager(): SessionManager
    fun okHttpClient(): OkHttpClient
    fun authRepository(): AuthRepository
}
