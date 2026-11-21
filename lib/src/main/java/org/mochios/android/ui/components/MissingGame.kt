// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.mochios.android.R

/**
 * Whether the launch router reopened game [id] of [feature]; see
 * [LastViewedStore.reopened]. Read once as the game's screen opens, and kept
 * across its recreation.
 */
@Composable
fun rememberReopened(feature: String, id: String): Boolean =
    rememberSaveable(feature, id) { LastViewedStore.reopened(feature, id) }

/**
 * The pane for a game the server no longer has, deleted from another device.
 * The stored id is forgotten so the next launch does not open it again. A game
 * the router [reopened] gives way to the empty pane ([onEmpty]) at once; one
 * opened from a link or a notification says it is gone, with a way back.
 */
@Composable
fun MissingGame(
    feature: String,
    id: String,
    reopened: Boolean,
    onEmpty: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(feature, id) {
        LastViewedStore.drop(context, feature, id)
        if (reopened) onEmpty()
    }
    if (!reopened) {
        NotFoundState(title = stringResource(R.string.game_not_found), onBack = onEmpty)
    }
}
