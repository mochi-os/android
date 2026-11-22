// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.theme.LocalSpacing

/**
 * The digits of [raw] as ASCII, at most [length] of them, for a one-time-code
 * field. A keyboard may write Arabic-Indic, Devanagari or full-width digits,
 * and the server compares a code against 0-9 alone, so each digit of any
 * script becomes its ASCII digit; anything that is not a digit is dropped.
 */
fun codeDigits(raw: String, length: Int): String = buildString {
    var index = 0
    while (index < raw.length && this.length < length) {
        val point = raw.codePointAt(index)
        val digit = Character.digit(point, 10)
        if (digit >= 0) append('0' + digit)
        index += Character.charCount(point)
    }
}

/**
 * Segmented one-time-code input: [length] boxes over a single digit-only value.
 * [onFilled] fires once the last digit is entered, letting callers auto-submit.
 */
@Composable
fun CodeInputBoxes(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    enabled: Boolean = true,
    onFilled: (String) -> Unit = {}
) {
    BasicTextField(
        value = value,
        onValueChange = { raw ->
            val sanitised = codeDigits(raw, length)
            if (sanitised != value) {
                onValueChange(sanitised)
                if (sanitised.length == length) {
                    onFilled(sanitised)
                }
            }
        },
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { if (value.length == length) onFilled(value) }),
        modifier = modifier,
        decorationBox = { _ ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(length) { index ->
                    if (index > 0) {
                        Spacer(modifier = Modifier.width(LocalSpacing.current.gap(8.dp)))
                    }
                    CodeCell(
                        char = value.getOrNull(index),
                        focused = index == value.length,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    )
}

@Composable
private fun CodeCell(
    char: Char?,
    focused: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor = if (focused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(LocalSpacing.current.gap(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = char?.toString() ?: "",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
