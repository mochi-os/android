// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.ConfirmDialog
import org.mochios.android.ui.components.DataChip
import org.mochios.android.ui.components.Section
import org.mochios.android.ui.components.Truncate
import org.mochios.feeds.R
import org.mochios.feeds.model.Feed
import org.mochios.android.R as MochiR

/**
 * Owner "Settings" tab: editable identity, banner, and delete sections. Only
 * shown to viewers who can manage the feed; subscribers get the read-only
 * identity view in [FeedSettingsScreen] instead.
 */
@Composable
fun GeneralTab(
    viewModel: FeedSettingsViewModel,
    onFeedDeleted: () -> Unit
) {
    val feedInfo by viewModel.feedInfo.collectAsState()
    // The banner arrives with the feed information load; no separate fetch.
    val banner = feedInfo?.banner.orEmpty()
    var bannerDraft by remember(banner) { mutableStateOf(banner) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        feedInfo?.let { info ->
            FeedIdentitySection(
                feed = info,
                editable = true,
                onRename = { newName ->
                    viewModel.setFeedName(newName)
                    viewModel.saveFeedName()
                },
            )
        }

        // Banner
        Section(
            title = stringResource(R.string.feeds_banner),
            description = stringResource(R.string.feeds_banner_description),
        ) {
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)) {
                OutlinedTextField(
                    value = bannerDraft,
                    onValueChange = { value -> bannerDraft = value },
                    placeholder = { Text(stringResource(R.string.feeds_banner_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.saveBanner(bannerDraft)
                        focusManager.clearFocus()
                    },
                    enabled = bannerDraft != banner,
                ) {
                    Text(stringResource(MochiR.string.common_save))
                }
            }
        }

        // Delete feed
        Section(
            title = stringResource(R.string.feeds_delete_feed),
            description = stringResource(R.string.feeds_delete_feed_description),
            action = {
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                ) {
                    Text(stringResource(MochiR.string.common_delete))
                }
            },
            content = {},
        )
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = stringResource(R.string.feeds_delete_feed),
            message = stringResource(R.string.feeds_delete_feed_confirm),
            confirmLabel = stringResource(MochiR.string.common_delete),
            isDestructive = true,
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteFeed { onFeedDeleted() }
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

/**
 * Identity card shared by the owner Settings tab and the subscriber view.
 * Shows the feed name (editable when [editable]), entity ID, fingerprint, and
 * server. Identifier values render as copyable [DataChip]s.
 *
 * @param onRename Invoked with the trimmed new name when the owner saves an
 *                 edit. Ignored when [editable] is false.
 */
@Composable
fun FeedIdentitySection(
    feed: Feed,
    editable: Boolean,
    onRename: (String) -> Unit,
) {
    Section(title = stringResource(R.string.feeds_settings_section_identity)) {
        IdentityFieldRow(label = stringResource(R.string.feeds_name)) {
            if (editable) {
                NameEditor(
                    currentName = feed.name,
                    onRename = onRename,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(feed.name)
            }
        }
        IdentityFieldRow(label = stringResource(R.string.feeds_settings_field_entity_id)) {
            DataChip(value = feed.id, truncate = Truncate.MIDDLE)
        }
        if (feed.fingerprint.isNotBlank()) {
            IdentityFieldRow(label = stringResource(R.string.feeds_settings_field_fingerprint)) {
                DataChip(value = feed.fingerprint, truncate = Truncate.MIDDLE)
            }
        }
        if (!feed.server.isNullOrBlank()) {
            IdentityFieldRow(label = stringResource(R.string.feeds_settings_field_server)) {
                DataChip(value = feed.server!!, truncate = Truncate.MIDDLE)
            }
        }
    }
}

/**
 * Identity row with a fixed-width label so values align in a column. Matches
 * the account settings identity layout rather than the right-aligned shared
 * [org.mochios.android.ui.components.FieldRow].
 */
@Composable
private fun IdentityFieldRow(
    label: String,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        content()
    }
}

@Composable
private fun NameEditor(
    currentName: String,
    onRename: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isEditing by remember { mutableStateOf(false) }
    var editValue by remember(currentName) { mutableStateOf(currentName) }

    if (isEditing) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedTextField(
                value = editValue,
                onValueChange = { value -> editValue = value },
                singleLine = true,
                trailingIcon = if (editValue.isNotEmpty()) {
                    {
                        IconButton(onClick = { editValue = "" }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.feeds_clear),
                            )
                        }
                    }
                } else {
                    null
                },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                onRename(editValue.trim())
                isEditing = false
            }) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(R.string.feeds_save_name),
                )
            }
            IconButton(onClick = {
                editValue = currentName
                isEditing = false
            }) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.feeds_settings_name_cancel_cd),
                )
            }
        }
    } else {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            // fill = false keeps the pencil next to the name instead of pushed to
            // the far end, while the weight still lets a long name wrap.
            Text(currentName, modifier = Modifier.weight(1f, fill = false))
            IconButton(
                onClick = {
                    editValue = currentName
                    isEditing = true
                },
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.feeds_settings_name_edit_cd),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
