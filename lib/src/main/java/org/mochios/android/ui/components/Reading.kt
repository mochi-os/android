// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

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
