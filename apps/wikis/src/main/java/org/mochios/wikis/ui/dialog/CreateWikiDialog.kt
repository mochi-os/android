// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.wikis.R

/**
 * Reusable "Create wiki" dialog. Mirrors the web `CreateEntityDialog` usage
 * in `apps/wikis/web/src/components/layout/wiki-layout.tsx` lines 150-162.
 *
 * Pure-input — does not talk to the repository. Callers (typically
 * `WikiListScreen`) wire [onSubmit] to `WikisRepository.createWiki` and
 * navigate to the new wiki's home on success.
 *
 * Privacy is exposed as a single switch labelled "Allow anyone to search
 * for wiki" — on -> `public`, off -> `private`. Default off.
 */
@Composable
fun CreateWikiDialog(
    open: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (name: String, privacy: String) -> Unit,
    isPending: Boolean = false,
) {
    if (!open) return

    var name by remember { mutableStateOf("") }
    var allowSearch by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isPending) onDismiss() },
        title = { Text(stringResource(R.string.wikis_create_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.wikis_create_name_label)) },
                    singleLine = true,
                    enabled = !isPending,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.wikis_create_privacy_label),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = allowSearch,
                        onCheckedChange = { allowSearch = it },
                        enabled = !isPending,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotEmpty() && !isPending) {
                        onSubmit(trimmed, if (allowSearch) "public" else "private")
                    }
                },
                enabled = name.trim().isNotEmpty() && !isPending,
            ) {
                if (isPending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.wikis_create_submit))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isPending) {
                Text(stringResource(R.string.wikis_create_cancel))
            }
        },
    )
}
