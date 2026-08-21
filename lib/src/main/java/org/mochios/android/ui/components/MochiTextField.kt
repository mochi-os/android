// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.theme.LocalEntityRadius

/** The borderless, tonal colour set behind [MochiTextField]. */
@Composable
fun mochiTextFieldColors(): TextFieldColors = TextFieldDefaults.colors(
    focusedContainerColor = mochiFieldFocusedContainer(),
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    errorContainerColor = MaterialTheme.colorScheme.errorContainer,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    errorIndicatorColor = Color.Transparent,
)

/**
 * The container an active field carries: the resting tone, tinted toward
 * primary.
 *
 * Focus cannot be another step of the neutral ramp. A field rests on
 * `surfaceContainerHighest`, which is already the far end of that ramp in both
 * themes — the darkest step in light, the lightest in dark — so there is no
 * further step in the direction that reads as deepening, and the step that
 * does exist goes back toward the card the field sits on. A tint carries the
 * state instead, and carries it the same way in either theme.
 */
@Composable
fun mochiFieldFocusedContainer(): Color =
    MaterialTheme.colorScheme.primary
        .copy(alpha = 0.10f)
        .compositeOver(MaterialTheme.colorScheme.surfaceContainerHighest)

/**
 * The app's text field: a tonal box, no border, no underline.
 *
 * Drop-in for [androidx.compose.material3.OutlinedTextField] — same parameter
 * names and order — so the two are interchangeable at a call site. What
 * changes is the resting appearance. An outlined field draws a 1 dp stroke
 * around every input on the screen, which is the Material 2 look and what made
 * the forms read as dated; a filled field separates itself from the page with
 * a tone instead, and only borrows a colour when it is focused or in error.
 *
 * Material's own filled field still draws an indicator line under the text,
 * which is the same 2014 idiom one edge at a time, so all four indicator
 * colours are cleared here. Focus reads through the container taking a tint of
 * primary (see [mochiFieldFocusedContainer]), and an error through the
 * container going to `errorContainer` — no stroke in either case.
 *
 * The corner follows [LocalEntityRadius] so the field honours the reader's
 * radius preference alongside cards and dialogs, with the bottom corners
 * squared off slightly less than the top to keep the label from crowding.
 */
@Composable
fun MochiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape? = null,
    colors: TextFieldColors? = null,
) {
    val radius = LocalEntityRadius.current
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource,
        shape = shape ?: RoundedCornerShape(
            topStart = radius,
            topEnd = radius,
            bottomStart = (radius.value - 4f).coerceAtLeast(0f).dp,
            bottomEnd = (radius.value - 4f).coerceAtLeast(0f).dp,
        ),
        colors = colors ?: mochiTextFieldColors(),
    )
}

/**
 * [TextFieldValue] overload, for callers that drive the selection or cursor
 * themselves (mention autocomplete, for one) rather than just the text.
 */
@Composable
fun MochiTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape? = null,
    colors: TextFieldColors? = null,
) {
    val radius = LocalEntityRadius.current
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource,
        shape = shape ?: RoundedCornerShape(
            topStart = radius,
            topEnd = radius,
            bottomStart = (radius.value - 4f).coerceAtLeast(0f).dp,
            bottomEnd = (radius.value - 4f).coerceAtLeast(0f).dp,
        ),
        colors = colors ?: mochiTextFieldColors(),
    )
}
