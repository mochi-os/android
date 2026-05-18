package org.mochios.people.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import org.mochios.people.R

/**
 * Hex colour picker dialog backing the Profile editor's "Accent" field.
 * Mirrors the web `<ColourPicker>` component: a 12-swatch grid of common
 * Material accent presets plus a free-form `#RGB` / `#RRGGBB` input. Tapping
 * a preset fills the input; Save fires `onConfirm` with the validated hex.
 *
 * Empty input is allowed and means "clear accent" — the same UX as the web
 * picker's `onClear` button.
 */
@Composable
fun ColourPickerDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    val trimmed = draft.trim()
    val isValid = trimmed.isEmpty() || ACCENT_PATTERN.matches(trimmed)
    val previewColour: Color? = if (isValid && trimmed.isNotEmpty()) parseHex(trimmed) else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.people_profile_accent)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(previewColour ?: MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape,
                            ),
                    )
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        placeholder = { Text("#a78bfa") },
                        isError = !isValid,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Text(
                    stringResource(R.string.people_profile_accent_presets),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Two rows of six swatches → 12 presets. Drawn as a vertical
                // stack of Rows to avoid pulling LazyGrid into the dialog.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESETS.chunked(6).forEach { chunk ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            chunk.forEach { hex ->
                                SwatchButton(
                                    hex = hex,
                                    selected = trimmed.equals(hex, ignoreCase = true),
                                    onClick = { draft = hex },
                                )
                            }
                        }
                    }
                }

                if (!isValid) {
                    Spacer(Modifier.height(0.dp))
                    Text(
                        text = stringResource(R.string.people_profile_accent_invalid),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = isValid,
            ) {
                Text(stringResource(R.string.people_common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.people_common_cancel))
            }
        },
    )
}

@Composable
private fun SwatchButton(
    hex: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colour = parseHex(hex) ?: Color.Gray
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(colour)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick),
    )
    Spacer(Modifier.width(0.dp))
}

private val ACCENT_PATTERN = Regex("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$")

// 12 Material-ish 500-weight presets — covers a sensible default palette
// without dragging in the full Material colour table.
private val PRESETS = listOf(
    "#ef4444", // red
    "#f97316", // orange
    "#f59e0b", // amber
    "#84cc16", // lime
    "#22c55e", // green
    "#10b981", // emerald
    "#06b6d4", // cyan
    "#3b82f6", // blue
    "#6366f1", // indigo
    "#a78bfa", // violet
    "#ec4899", // pink
    "#64748b", // slate
)

private fun parseHex(hex: String): Color? {
    val s = hex.trim().removePrefix("#")
    return try {
        when (s.length) {
            6 -> Color(
                red = s.substring(0, 2).toInt(16) / 255f,
                green = s.substring(2, 4).toInt(16) / 255f,
                blue = s.substring(4, 6).toInt(16) / 255f,
            )
            3 -> Color(
                red = (s[0].digitToInt(16) * 17) / 255f,
                green = (s[1].digitToInt(16) * 17) / 255f,
                blue = (s[2].digitToInt(16) * 17) / 255f,
            )
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}
