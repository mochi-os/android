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

/**
 * Segmented one-time-code input. Renders [length] equal-width boxes backed by a
 * single text value; typing fills the boxes left to right and the box past the
 * last entered digit is highlighted as the cursor.
 *
 * Only digits are kept and the value is clamped to [length]. [onFilled] fires
 * once the final digit is entered, letting callers auto-submit.
 *
 * @param value the current code (already digit-only, up to [length] chars)
 * @param onValueChange invoked with the sanitised, length-clamped value
 * @param modifier layout modifier for the row of boxes
 * @param length number of boxes / expected code length
 * @param enabled whether input is accepted
 * @param onFilled invoked with the value once it reaches [length] digits
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
            val sanitised = raw.filter { char -> char.isDigit() }.take(length)
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
                        Spacer(modifier = Modifier.width(8.dp))
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
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = char?.toString() ?: "",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
