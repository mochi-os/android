// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The header every bottom sheet opens with: [title] on the left, after
 * [leading] when there is one and over [subtitle] when there is one, then the
 * sheet's [actions] on the right, with a divider beneath. It sits directly
 * under the sheet's drag handle and spans the sheet's full width, so a sheet
 * pads its own content, not this. There is no close button: a swipe, a tap
 * outside and back all close a sheet.
 *
 * @param title What the sheet is about, in the one title style every sheet uses.
 * @param modifier Applied to the header as a whole.
 * @param subtitle A line of context under the title, such as a record's class.
 * @param titleColor The title's colour, for a title that stands in for a
 * placeholder, such as an untitled event.
 * @param titleDecoration Drawn over the title, such as a cancelled event's strike.
 * @param titleModifier Applied to the title alone.
 * @param leading Drawn before the title, such as an event's colour.
 * @param actions The sheet's own icon buttons, before the close button.
 */
@Composable
fun MochiSheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleColor: Color = Color.Unspecified,
    titleDecoration: TextDecoration? = null,
    titleModifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(start = 16.dp, end = 4.dp),
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = titleColor,
                    textDecoration = titleDecoration,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = titleModifier,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            actions()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
