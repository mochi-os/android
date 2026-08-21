// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * What a button is for, which is what decides its colour.
 *
 * Tint used to be chosen at the call site, one `ButtonDefaults.*Colors` at a
 * time, and it drifted: the same revoke was error-red in the shared access card
 * and neutral grey two files away, while closing an account — the one
 * irreversible action in the app — was drawn exactly like exporting a backup.
 * Naming the role instead means a button cannot disagree with what it does.
 */
enum class MochiButtonTone {
    /** The action the screen is for. */
    Primary,

    /** An action that must not compete with the primary one beside it. */
    Neutral,

    /** Deletes, revokes, closes. Anything the reader cannot undo. */
    Destructive,
}

/**
 * The app's filled button: the one action a screen is for.
 *
 * Drop-in for [androidx.compose.material3.Button] — same parameter names and
 * order — with [tone] added ahead of the colour parameters. Pass [colors] only
 * for something no tone covers; it wins over [tone] when both are given.
 */
@Composable
fun MochiButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: MochiButtonTone = MochiButtonTone.Primary,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors? = null,
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors ?: filledColors(tone),
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

/**
 * The app's outlined button: an action that sits beside the primary one, or
 * stands alone in a section without claiming the whole screen.
 *
 * Drop-in for [androidx.compose.material3.OutlinedButton], with [tone] added.
 * Tinted by default. Material 3's own outlined button went neutral in the
 * expressive update — label `onSurfaceVariant`, border `outlineVariant` — which
 * left the app with two weights, filled-blue or grey, and "Add rule" reading
 * like something already disabled. Pass [MochiButtonTone.Neutral] for a button
 * that genuinely must not compete, such as a Clear beside a Save.
 */
@Composable
fun MochiOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: MochiButtonTone = MochiButtonTone.Primary,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors? = null,
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors ?: outlinedColors(tone),
        elevation = elevation,
        border = border ?: outlinedBorder(tone, enabled),
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

/**
 * The app's text button: dialog answers, and actions inside a row that already
 * has a container of its own.
 *
 * Drop-in for [androidx.compose.material3.TextButton], with [tone] added.
 */
@Composable
fun MochiTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: MochiButtonTone = MochiButtonTone.Neutral,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors? = null,
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors ?: textColors(tone),
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

/**
 * The app's icon button.
 *
 * Drop-in for [androidx.compose.material3.IconButton], with [tone] added.
 * [MochiButtonTone.Neutral] inherits the surrounding content colour, which is
 * what an icon in a top bar or a list row wants; the other two colour the
 * button, so the [androidx.compose.material3.Icon] inside can be left untinted
 * and cannot disagree with it.
 */
@Composable
fun MochiIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: MochiButtonTone = MochiButtonTone.Neutral,
    colors: IconButtonColors? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors ?: iconColors(tone),
        interactionSource = interactionSource,
        content = content,
    )
}

/**
 * The app's tonal button: the middle weight, for a section's own action on a
 * screen where several sections each have one and none of them is the screen's
 * main action.
 *
 * Drop-in for [androidx.compose.material3.FilledTonalButton], with [tone]
 * added.
 */
@Composable
fun MochiTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: MochiButtonTone = MochiButtonTone.Primary,
    shape: Shape = ButtonDefaults.filledTonalShape,
    colors: ButtonColors? = null,
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors ?: tonalColors(tone),
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
private fun tonalColors(tone: MochiButtonTone): ButtonColors = when (tone) {
    MochiButtonTone.Primary -> ButtonDefaults.filledTonalButtonColors()
    MochiButtonTone.Neutral -> ButtonDefaults.filledTonalButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    MochiButtonTone.Destructive -> ButtonDefaults.filledTonalButtonColors(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    )
}

@Composable
private fun filledColors(tone: MochiButtonTone): ButtonColors = when (tone) {
    MochiButtonTone.Primary -> ButtonDefaults.buttonColors()
    MochiButtonTone.Neutral -> ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    MochiButtonTone.Destructive -> ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
    )
}

@Composable
private fun outlinedColors(tone: MochiButtonTone): ButtonColors = when (tone) {
    MochiButtonTone.Primary -> ButtonDefaults.outlinedButtonColors(
        contentColor = MaterialTheme.colorScheme.primary,
    )
    MochiButtonTone.Neutral -> ButtonDefaults.outlinedButtonColors(
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    MochiButtonTone.Destructive -> ButtonDefaults.outlinedButtonColors(
        contentColor = MaterialTheme.colorScheme.error,
    )
}

/**
 * A border in the tone's own colour. Material draws every outlined button on
 * `outlineVariant`, which is a hairline against a card and says nothing about
 * what the button does.
 */
@Composable
private fun outlinedBorder(tone: MochiButtonTone, enabled: Boolean): BorderStroke? {
    if (!enabled) return ButtonDefaults.outlinedButtonBorder(enabled = false)
    val color = when (tone) {
        MochiButtonTone.Primary -> MaterialTheme.colorScheme.primary
        MochiButtonTone.Neutral -> MaterialTheme.colorScheme.outline
        MochiButtonTone.Destructive -> MaterialTheme.colorScheme.error
    }
    return BorderStroke(1.dp, color.copy(alpha = 0.5f))
}

@Composable
private fun textColors(tone: MochiButtonTone): ButtonColors = when (tone) {
    MochiButtonTone.Primary -> ButtonDefaults.textButtonColors(
        contentColor = MaterialTheme.colorScheme.primary,
    )
    MochiButtonTone.Neutral -> ButtonDefaults.textButtonColors(
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    MochiButtonTone.Destructive -> ButtonDefaults.textButtonColors(
        contentColor = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun iconColors(tone: MochiButtonTone): IconButtonColors = when (tone) {
    MochiButtonTone.Primary -> IconButtonDefaults.iconButtonColors(
        contentColor = MaterialTheme.colorScheme.primary,
    )
    MochiButtonTone.Neutral -> IconButtonDefaults.iconButtonColors()
    MochiButtonTone.Destructive -> IconButtonDefaults.iconButtonColors(
        contentColor = MaterialTheme.colorScheme.error,
    )
}
