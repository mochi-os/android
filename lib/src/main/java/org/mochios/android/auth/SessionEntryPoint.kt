// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.auth

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Lets non-Hilt call sites — notably composables in the shared UI layer — reach
 * the singleton [SessionManager] via
 * `EntryPointAccessors.fromApplication(context, SessionEntryPoint::class.java)`.
 * Used by [org.mochios.android.ui.components.rememberServerUrl] so avatar and
 * asset composables can resolve relative paths without every screen threading
 * the server URL through.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SessionEntryPoint {

    fun sessionManager(): SessionManager
}
