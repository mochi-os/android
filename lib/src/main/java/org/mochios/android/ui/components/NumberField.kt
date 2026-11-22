// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

/** A number on its way to being typed: "-", ".", "5.", "-.5", "5,2". */
private val PARTIAL = Regex("""^-?\d*([.,]\d*)?$""")

/** A number as the server stores one (core's number check). */
private val COMPLETE = Regex("""^-?\d+(\.\d+)?$""")

/**
 * Whether [text] is a number, or the start of one, as typed: an optional
 * minus, digits, and one decimal point or comma.
 */
fun isPartialNumber(text: String): Boolean = PARTIAL.matches(text)

/**
 * The number [text] stands for, as the server stores one: a decimal comma
 * becomes a point, a bare fraction gains its zero (".5" is "0.5") and a
 * trailing point is dropped ("5." is "5"). "" for an empty field, which clears
 * the value; null while [text] is not yet a number ("-", ".") or never will be.
 */
fun completeNumber(text: String): String? {
    if (text.isEmpty()) return ""
    if (!PARTIAL.matches(text)) return null
    val pointed = text.replace(',', '.').removeSuffix(".")
    val number = when {
        pointed.startsWith("-.") -> "-0" + pointed.substring(1)
        pointed.startsWith(".") -> "0$pointed"
        else -> pointed
    }
    return number.takeIf { candidate -> COMPLETE.matches(candidate) }
}

/**
 * A number field's editor. It holds what is typed, so a minus sign, a leading
 * point or a trailing one can be typed on the way to a number, and reports
 * through [onValueChange] only a complete number ([completeNumber]) that
 * differs from [value], or "" when the field is emptied. A new [value] from
 * outside replaces the text unless the text already stands for it. The
 * keyboard is the decimal one.
 *
 * @param value The stored value.
 * @param onValueChange Called with each complete number typed, or "".
 * @param modifier Modifier applied to the text field.
 * @param label The field's label, if it shows one.
 */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
) {
    var text by rememberSaveable { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (completeNumber(text) != value) text = value
    }
    MochiTextField(
        value = text,
        onValueChange = { typed ->
            // A stored value that is no number can still be deleted from.
            if (isPartialNumber(typed) || (!isPartialNumber(text) && typed.length < text.length)) {
                text = typed
                val number = completeNumber(typed)
                if (number != null && number != value) onValueChange(number)
            }
        },
        label = label,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}
