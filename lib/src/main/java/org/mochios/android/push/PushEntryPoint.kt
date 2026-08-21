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
import org.mochios.android.notifications.NotificationsRepository

/**
 * Hilt entry point for the push package: its classes are framework-instantiated
 * or DI-less, so they reach the graph via EntryPointAccessors rather than
 * injection.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PushEntryPoint {
    fun sessionManager(): SessionManager
    fun okHttpClient(): OkHttpClient
    fun authRepository(): AuthRepository
    fun notificationsRepository(): NotificationsRepository
    fun pushAccountStore(): PushAccountStore
}
