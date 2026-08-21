// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.theme.LocalEntityRadius

/**
 * What a surface floating over the page looks like: menus, and the pickers that
 * open a panel under a field.
 *
 * Each of those grew its own answer — an 8dp shadow here, a 2dp tonal tint
 * there, a card on `surfaceVariant` in a third — so three panels doing one job
 * arrived on screen looking like three different things. They read from here
 * instead.
 */

/** Menus round harder than list entities do, but still follow the user's radius setting. */
private val PopupMinRadius = 16.dp

/** The shadow a floating surface casts, matching `MenuTokens.ContainerElevation`. */
val MochiPopupElevation = 3.dp

/**
 * The container a floating surface carries: a step above the cards it opens
 * over, so a menu never lands on its own background.
 */
@Composable
fun mochiPopupContainerColor(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

/**
 * The corner a floating surface takes: the reader's radius preference, floored
 * at [PopupMinRadius] because the Material default of 4dp reads as a square
 * panel.
 */
@Composable
fun mochiPopupShape(): Shape =
    RoundedCornerShape(LocalEntityRadius.current.coerceAtLeast(PopupMinRadius))
