// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.auth

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Reaches the singleton [SessionManager] from non-Hilt call sites, chiefly
 * composables.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SessionEntryPoint {

    fun sessionManager(): SessionManager
}
