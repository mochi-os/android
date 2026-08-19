// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.theme.LocalEntityRadius

/**
 * A closed dropdown: the chosen value, a label over it, and a chevron.
 *
 * Use this rather than anchoring the menu to a read-only [MochiTextField].
 * A text field is an editor, and an editor clips its text — a value too wide
 * for the field is cut mid-word with nothing to say it was cut, which is how
 * "Create, edit, comment, and view" came to read "Create, edit, comment, and
 * vie". This draws the value as text, so it ellipsises and stays honest about
 * being shortened. It carries no cursor, no selection and no keyboard either,
 * none of which a picker ever wanted.
 *
 * The container tone and corner match [MochiTextField], so a dropdown sitting
 * in a column of fields still reads as one of them.
 *
 * @param value the chosen option, already resolved to its label.
 * @param expanded whether the menu is open; the caller owns the state.
 * @param onExpandedChange asked to open or close.
 * @param label sits above the value, in the field-label style; null omits it
 *   and centres the value in the same height.
 * @param enabled whether the field opens on tap.
 * @param placeholder shown in place of [value] while nothing is chosen.
 * @param menuContent the options, normally [MochiDropdownMenuItem]s.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MochiDropdownField(
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    placeholder: String? = null,
    menuContent: @Composable ColumnScope.() -> Unit,
) {
    val radius = LocalEntityRadius.current
    val shown = value.ifEmpty { placeholder.orEmpty() }
    val valueColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        value.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) onExpandedChange(it) },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = enabled)
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        topStart = radius,
                        topEnd = radius,
                        bottomStart = (radius.value - 4f).coerceAtLeast(0f).dp,
                        bottomEnd = (radius.value - 4f).coerceAtLeast(0f).dp,
                    )
                )
                .background(
                    if (expanded) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (label != null) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = shown,
                    style = MaterialTheme.typography.bodyLarge,
                    color = valueColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .rotate(if (expanded) 180f else 0f),
            )
        }

        MochiDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            content = menuContent,
        )
    }
}
