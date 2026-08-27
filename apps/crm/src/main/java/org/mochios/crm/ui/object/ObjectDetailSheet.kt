// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.`object`

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiBottomSheet
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiTab
import org.mochios.android.ui.components.MochiTabRow
import org.mochios.android.ui.components.SaveStatusIndicator
import org.mochios.crm.R
import org.mochios.crm.model.CrmDetails
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectDetailSheet(
    crmId: String,
    objectId: String,
    crmDetails: CrmDetails,
    initialObject: org.mochios.crm.model.CrmObject? = null,
    /**
     * Field ids the active view pins, in the order it lists them. The
     * Properties tab leads with these and follows with the rest of the class.
     */
    viewFieldIds: List<String> = emptyList(),
    onDismiss: () -> Unit,
    /**
     * Deletes this object. The sheet never deletes anything itself, so this is
     * a command, not a notification.
     */
    onDeleteObject: () -> Unit,
    onNavigateToObject: (String) -> Unit = {},
    viewModel: ObjectDetailViewModel = hiltViewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showOverflow by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(crmId, objectId) {
        viewModel.loadWithInitialObject(crmId, objectId, initialObject, crmDetails.crm.access)
    }

    // A failed auto-save is otherwise invisible — the field keeps showing
    // the edited value. Surface it so the user knows to retry.
    LaunchedEffect(Unit) {
        viewModel.saveFailed.collect {
            Toast.makeText(
                context,
                context.getString(MochiR.string.common_save_failed),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    // Comment/attachment failures are otherwise invisible while the sheet is
    // open (uiState.error only renders when no object is loaded) — the input
    // clears and nothing appears. Surface the actual error.
    LaunchedEffect(Unit) {
        viewModel.actionFailed.collect { error ->
            Toast.makeText(context, error.userMessage(), Toast.LENGTH_LONG).show()
        }
    }

    MochiBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        when {
            uiState.isLoading && uiState.obj == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null && uiState.obj == null -> {
                // Same height as the loading branch above: the error state needs
                // room for its icon and retry button, which 200.dp would clip.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    ErrorState(
                        error = uiState.error!!,
                        onRetry = {
                            viewModel.loadWithInitialObject(
                                crmId,
                                objectId,
                                initialObject,
                                crmDetails.crm.access
                            )
                        }
                    )
                }
            }

            uiState.obj != null -> {
                val obj = uiState.obj!!
                val objClass = crmDetails.classes.find { it.id == obj.objectClass }
                val titleFieldId = objClass?.title?.takeIf { it.isNotBlank() }
                val title = titleFieldId?.let { obj.stringValue(it) }.orEmpty()
                    .ifBlank { stringResource(R.string.crm_untitled) }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (objClass != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = objClass.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        MochiIconButton(onClick = { viewModel.toggleWatch() }) {
                            Icon(
                                imageVector = if (uiState.isWatching) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (uiState.isWatching) stringResource(R.string.crm_object_unwatch) else stringResource(R.string.crm_object_watch)
                            )
                        }

                        Box {
                            MochiIconButton(onClick = { showOverflow = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(MochiR.string.common_more_options))
                            }
                            MochiDropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false }
                            ) {
                                MochiDropdownMenuItem(
                                    text = { Text(stringResource(MochiR.string.common_delete)) },
                                    onClick = {
                                        showOverflow = false
                                        showDeleteConfirm = true
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                )
                            }
                        }
                    }

                    SaveStatusIndicator(
                        status = uiState.saveStatus,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                    // Tabs match the web layout: attachments and links are
                    // inline sections of Properties, watch is the header Eye
                    // icon.
                    val tabs = listOf(
                        stringResource(R.string.crm_object_tab_properties),
                        stringResource(R.string.crm_object_tab_comments),
                        stringResource(R.string.crm_object_tab_activity),
                    )
                    MochiTabRow(
                        tabs = tabs.map { title -> MochiTab(title) },
                        selectedIndex = uiState.selectedTab,
                        onSelect = { index -> viewModel.selectTab(index) },
                        containerColor = Color.Transparent,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Tab content
                    when (uiState.selectedTab) {
                        0 -> PropertiesTab(
                            obj = obj,
                            crmDetails = crmDetails,
                            viewModel = viewModel,
                            viewFieldIds = viewFieldIds,
                            onNavigateToObject = onNavigateToObject,
                            crmId = crmId,
                        )
                        1 -> CommentsTab(
                            comments = uiState.comments,
                            crmId = crmId,
                            onCreateComment = { content, parent, uris ->
                                viewModel.createComment(content, parent, uris)
                            },
                            resolveFileName = viewModel::fileName,
                            onUpdateComment = { id, content ->
                                viewModel.updateComment(id, content)
                            },
                            onDeleteComment = { id ->
                                viewModel.deleteComment(id)
                            },
                            onSearchUsers = { query -> viewModel.searchUsers(query) },
                            avatarUrlBuilder = { comment ->
                                "/crm/$crmId/-/comment/${comment.id}/asset/avatar"
                            }
                        )
                        2 -> ActivityTab(
                            activity = uiState.activity,
                            crmDetails = crmDetails,
                            avatarUrlBuilder = { entry ->
                                "/crm/$crmId/-/activity/${entry.id}/asset/avatar"
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        MochiAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = stringResource(R.string.crm_object_delete_title),
            text = stringResource(R.string.crm_object_delete_message),
            confirmText = stringResource(MochiR.string.common_delete),
            onConfirm = {
                showDeleteConfirm = false
                onDeleteObject()
            },
            destructive = true,
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }
}
