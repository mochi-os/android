// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.home.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import org.mochios.home.ui.HomeScreen

object HomeApp {
    /** The grid of apps, which the Mochi launcher icon opens. */
    const val HOME = "home"
}

/** Wire the home grid into the parent graph. */
fun NavGraphBuilder.homeNavGraph(onOpenNotifications: () -> Unit = {}) {
    composable(HomeApp.HOME) {
        HomeScreen(onOpenNotifications = onOpenNotifications)
    }
}
