// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.ui.detail.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mochios.words.R
import org.mochios.android.R as MochiR

/**
 * Modal letter picker for the blank tile. 26 buttons in a 7-wide grid
 * (better than the web's 9-wide for narrow Android screens). Tapping a
 * button calls `onSelect(letter)`; tapping the dismiss area or the Cancel
 * button calls `onDismiss`. Letter is uppercase 'A'..'Z'.
 *
 * The displayed letter on the board is fixed by this dialog; the
 * underlying rack tile stays `'_'` so the engine can score it correctly
 * (blanks always score 0 regardless of the chosen letter).
 */
@Composable
fun BlankTileDialog(
    onSelect: (Char) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.words_detail_blank_title),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                val rows = listOf(
                    listOf('A', 'B', 'C', 'D', 'E', 'F', 'G'),
                    listOf('H', 'I', 'J', 'K', 'L', 'M', 'N'),
                    listOf('O', 'P', 'Q', 'R', 'S', 'T', 'U'),
                    listOf('V', 'W', 'X', 'Y', 'Z'),
                )
                for ((rowIdx, row) in rows.withIndex()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .let { base ->
                                if (rowIdx == 0) base
                                else base.then(Modifier.padding(top = 4.dp))
                            },
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (letter in row) {
                            OutlinedButton(
                                onClick = { onSelect(letter) },
                                modifier = Modifier.size(40.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            ) {
                                Text(
                                    text = letter.toString(),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MochiR.string.common_cancel))
            }
        },
    )
}
