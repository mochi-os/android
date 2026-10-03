// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One line of an object's form. */
sealed interface FormRow<out F> {
    /** The title field, as the form's heading. */
    data class Heading<F>(val field: F) : FormRow<F>

    /** A field given the whole width. */
    data class Wide<F>(val field: F) : FormRow<F>

    /** Two short fields side by side. */
    data class Paired<F>(val first: F, val second: F) : FormRow<F>
}

/**
 * Lays [fields], already in the order the form lists them, out as rows. The
 * [title] field leads as the heading wherever it sat. With [pairs], a [short]
 * field shares its row with a short field directly after it; every other field
 * takes a row to itself.
 */
fun <F> formRows(
    fields: List<F>,
    title: (F) -> Boolean,
    short: (F) -> Boolean,
    pairs: Boolean = true,
): List<FormRow<F>> {
    val rows = mutableListOf<FormRow<F>>()
    fields.firstOrNull(title)?.let { field -> rows.add(FormRow.Heading(field)) }
    val rest = fields.filterNot(title)
    var index = 0
    while (index < rest.size) {
        val field = rest[index]
        val next = rest.getOrNull(index + 1)
        if (pairs && short(field) && next != null && short(next)) {
            rows.add(FormRow.Paired(field, next))
            index += 2
        } else {
            rows.add(FormRow.Wide(field))
            index += 1
        }
    }
    return rows
}

/**
 * Whether a field of this type holds a value brief enough to share a row: a
 * choice, a date, a number or a tick. Text does not, however few rows it asks
 * for, and nor does a person: a name beside an avatar and a clear button is
 * cut short in half the form, and the picker's search opens as wide as its
 * field.
 */
fun shortField(type: String): Boolean = type in SHORT_TYPES

private val SHORT_TYPES = setOf("enumerated", "date", "number", "checkbox")

/**
 * Whether a form [width] wide has room for two fields a row at this
 * [fontScale]. Half a narrow phone at a large text size cuts a value short,
 * which one field a row does not.
 */
fun formPairs(width: Dp, fontScale: Float): Boolean = width >= PAIR_WIDTH * 2 * fontScale

/** The least width a field sharing a row needs, at the default text size. */
private val PAIR_WIDTH = 150.dp

/**
 * Whether a choice field [cell] wide shows a name on one line: [text] is the
 * name's width on a single line, and [swatch] whether its colour dot sits
 * before it. A choice field sharing its row has about half the form's width,
 * and a name too long for that wraps, so such a field takes a row to itself.
 */
fun choiceFits(text: Dp, swatch: Boolean, cell: Dp): Boolean =
    text + CHOICE_FRAME + (if (swatch) CHOICE_SWATCH + CHOICE_SWATCH_GAP + CHOICE_PREFIX else 0.dp) <= cell

/** What a choice field spends beside its text: the padding before it and the arrow's slot after. */
private val CHOICE_FRAME = 68.dp

/** The colour dot before a choice's name, the space after it, and the padding the field adds to a prefix. */
private val CHOICE_SWATCH = 16.dp
private val CHOICE_SWATCH_GAP = 8.dp
private val CHOICE_PREFIX = 2.dp

/**
 * A choice's colour dot, for a text field's prefix. It sits in the text's own
 * line, not in the leading icon's slot: that slot is 48dp wide whatever it
 * holds, which in a field sharing its row is the difference between a name
 * fitting and wrapping.
 */
@Composable
fun ChoicePrefix(colour: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(CHOICE_SWATCH)
                .clip(CircleShape)
                .background(colour)
        )
        Spacer(modifier = Modifier.width(CHOICE_SWATCH_GAP))
    }
}

/**
 * An object's title as the heading of its form: all of it, wrapping over as
 * many lines as it takes, edited where it stands. A title is one line of text,
 * so a line break typed or pasted becomes a space. [name] is the field's name,
 * shown while the title is empty.
 */
@Composable
fun FormHeading(
    value: String,
    onValueChange: (String) -> Unit,
    name: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
) {
    val style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    if (readOnly) {
        Text(text = value, style = style, modifier = modifier.fillMaxWidth())
        return
    }
    val focus = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = { text -> onValueChange(oneLine(text)) },
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = name },
        decorationBox = { field ->
            if (value.isEmpty()) {
                Text(
                    text = name,
                    style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
            field()
        },
    )
}

/** [text] with each line break made a space, for a value that is one line however it wraps. */
fun oneLine(text: String): String = text.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')

/** A form field under its [label], which sits above it and takes none of its width. */
@Composable
fun FormField(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        content()
    }
}
