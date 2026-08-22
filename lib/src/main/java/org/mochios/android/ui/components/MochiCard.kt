// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

/**
 * The app's card: a tone that leaves room for the fields inside it.
 *
 * Drop-in for [androidx.compose.material3.Card] — same parameter names and
 * order, both overloads — so the two are interchangeable at a call site. What
 * changes is the container, and only because Material's own default cannot
 * work here: its filled card and its filled text field both resolve to
 * `surfaceContainerHighest`. That collision is invisible in Material's design
 * because its filled field still draws an indicator line under the text;
 * [mochiTextFieldColors] clears the indicator, so a field dropped into a
 * default card came out the exact same colour as the card and vanished — the
 * forum settings banner and the access-level pickers were unreadable this way,
 * present and tappable with nothing drawn.
 *
 * So a card takes `surfaceContainer` and leaves the top of the ramp to the
 * things that sit inside it. The stack reads page → card → input, each a step
 * apart, and the same three steps hold in dark where the ramp climbs instead
 * of falling. Pass [mochiDialogCardColors] for a card inside a dialog, which
 * starts a step further along and so has to go the other way.
 */
@Composable
fun MochiCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = mochiCardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        content = content,
    )
}

/**
 * Clickable overload, mirroring [androidx.compose.material3.Card]'s own. A card
 * that carries the tap ripple itself rather than through a `clickable` on its
 * modifier, so the ripple is clipped to the card's corner.
 */
@Composable
fun MochiCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = mochiCardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        interactionSource = interactionSource,
        content = content,
    )
}

/** The tone [MochiCard] carries: one step below an input, not the same step. */
@Composable
fun mochiCardColors(): CardColors = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
)

/**
 * Card colours for a card inside a dialog, which starts a step further along
 * the ramp than a page does.
 *
 * A dialog's own container is `surfaceContainerHigh`, so [mochiCardColors] has
 * nowhere to sit: one step from the dialog in the direction a card would go is
 * the tone the fields beside it already carry, and the list and the field
 * merge into one block. Going the other way instead — `surfaceContainerLow` —
 * separates from both, and reads the same way in either theme, since the ramp
 * falls away from the surface in light and climbs away from it in dark.
 */
@Composable
fun mochiDialogCardColors(): CardColors = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
)
