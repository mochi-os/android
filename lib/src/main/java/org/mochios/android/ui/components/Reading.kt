// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Draws [content] with the value of [state], read here rather than by the
 * caller, so a change to it redraws only [content]. A composer's text is the
 * case it is for: each keystroke then leaves the screen around it alone.
 *
 * @param state The state whose value [content] draws.
 * @param content What is drawn from the value.
 */
@Composable
fun <T> Reading(state: State<T>, content: @Composable (T) -> Unit) {
    content(state.value)
}

/**
 * The part of this flow's state that [part] picks, as state that changes only
 * when that part does, so a screen drawn from it is left alone while the rest
 * changes: a conversation while its composer's text does, with the text
 * itself picked into its own state and read through [Reading].
 *
 * The first value is picked from the flow's value once, as the screen first
 * draws; from then on the state follows the flow.
 *
 * @param part Picks the part from the whole. Taken with the flow on the first
 *   draw, so it must depend on nothing but the state it is given.
 */
@Composable
fun <T, R> StateFlow<T>.collectPartAsState(part: (T) -> R): State<R> {
    val flow = this
    val parts = remember(flow) { flow.map(part).distinctUntilChanged() }
    return parts.collectAsState(initial = remember(flow) { part(flow.value) })
}
