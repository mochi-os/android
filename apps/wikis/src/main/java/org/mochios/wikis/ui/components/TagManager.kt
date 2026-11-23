// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.wikis.R
import org.mochios.android.R as MochiR

/**
 * Tag chips and an add chip for the page footer. The tags are the page's, as
 * its view model holds them; adding and removing go back to it through
 * [onAdd] and [onRemove], so no list of its own can outlive a later reload.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagManager(
    tags: List<String>,
    canEdit: Boolean,
    onTagClick: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var addDialogOpen by remember { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<String?>(null) }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tags.forEach { tag ->
            AssistChip(
                onClick = { onTagClick(tag) },
                label = { Text(tag) },
                leadingIcon = {
                    Icon(
                        Icons.Default.LocalOffer,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
                trailingIcon = if (canEdit) {
                    {
                        // Render a discrete close affordance so the user has
                        // a visible "remove" target without needing to know
                        // about long-press. Tapping the X opens the
                        // confirmation dialog (the chip body still routes to
                        // the per-tag pages list).
                        MochiIconButton(
                            onClick = { pendingRemoval = tag },
                            modifier = Modifier.size(20.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(
                                    R.string.wikis_tag_manager_remove_confirm_title
                                ),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                } else null,
            )
        }

        if (canEdit) {
            AssistChip(
                onClick = { addDialogOpen = true },
                label = { Text(stringResource(R.string.wikis_tag_manager_add_action)) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
            )
        }
    }

    if (addDialogOpen) {
        AddTagDialog(
            open = true,
            existingTags = tags,
            onDismiss = { addDialogOpen = false },
            onConfirm = { newTag ->
                onAdd(newTag)
                addDialogOpen = false
            },
        )
    }

    val toRemove = pendingRemoval
    if (toRemove != null) {
        MochiAlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = stringResource(R.string.wikis_tag_manager_remove_confirm_title),
            text = stringResource(
                R.string.wikis_tag_manager_remove_confirm_message,
                toRemove,
            ),
            confirmText = stringResource(MochiR.string.common_delete),
            onConfirm = {
                onRemove(toRemove)
                pendingRemoval = null
            },
            destructive = true,
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }
}

/**
 * Add-tag dialog. Tags are trimmed and lowercased; blanks and duplicates are
 * refused inline.
 */
@Composable
fun AddTagDialog(
    open: Boolean,
    existingTags: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    if (!open) return

    var newTag by remember { mutableStateOf("") }
    var inlineError by remember { mutableStateOf<Int?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val submit: () -> Unit = {
        val cleaned = newTag.trim().lowercase()
        when {
            cleaned.isEmpty() -> {
                inlineError = R.string.wikis_tag_dialog_empty
            }
            existingTags.any { it.equals(cleaned, ignoreCase = true) } -> {
                inlineError = R.string.wikis_tag_dialog_duplicate
            }
            else -> {
                inlineError = null
                onConfirm(cleaned)
            }
        }
    }

    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.wikis_tag_dialog_title),
        content = {
            Column {
                MochiTextField(
                    value = newTag,
                    onValueChange = {
                        newTag = it
                        inlineError = null
                    },
                    label = { Text(stringResource(R.string.wikis_tag_dialog_label)) },
                    singleLine = true,
                    isError = inlineError != null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                val errorRes = inlineError
                if (errorRes != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(errorRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmText = stringResource(R.string.wikis_tag_dialog_add),
        onConfirm = submit,
        confirmEnabled = newTag.trim().isNotEmpty(),
        dismissText = stringResource(R.string.wikis_tag_dialog_cancel),
    )
}
