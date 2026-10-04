// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.`object`

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.model.Comment
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiEditorSheet
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiSheetHeader
import org.mochios.android.ui.components.MochiTab
import org.mochios.android.ui.components.MochiTabRow
import org.mochios.android.ui.components.SaveStatusIndicator
import org.mochios.projects.R
import org.mochios.projects.model.ProjectDetails
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectDetailSheet(
    projectId: String,
    objectId: String,
    projectDetails: ProjectDetails,
    initialObject: org.mochios.projects.model.ProjectObject? = null,
    onDismiss: () -> Unit,
    /**
     * Deletes this object. The sheet never deletes anything itself, so this is
     * a command, not a notification.
     */
    onDeleteObject: () -> Unit,
    onViewDiff: (String, String, String, String) -> Unit,
    onNavigateToObject: (String) -> Unit = {},
    onAddChild: (parent: String) -> Unit = {},
    viewModel: ObjectDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showOverflow by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(projectId, objectId) {
        viewModel.loadWithInitialObject(projectId, objectId, initialObject, projectDetails.project.access)
    }

    // A failed auto-save is otherwise invisible — the field keeps showing
    // the edited value. Surface it so the user knows to retry.
    LaunchedEffect(Unit) {
        viewModel.saveFailed.collect { error ->
            Toast.makeText(context, error.userMessage(), Toast.LENGTH_LONG).show()
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

    MochiEditorSheet(onDismissRequest = onDismiss) {
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
                                projectId,
                                objectId,
                                initialObject,
                                projectDetails.project.access
                            )
                        }
                    )
                }
            }

            uiState.obj != null -> {
                val obj = uiState.obj!!
                val prefix = projectDetails.project.prefix
                val objClass = projectDetails.classes.find { it.id == obj.objectClass }

                Column(modifier = Modifier.fillMaxSize()) {
                    MochiSheetHeader(
                        title = if (prefix.isNotBlank()) {
                            "$prefix-${obj.number}"
                        } else {
                            "#${obj.number}"
                        },
                        subtitle = objClass?.name,
                        actions = {
                            MochiIconButton(onClick = { viewModel.toggleWatch() }) {
                                Icon(
                                    imageVector = if (uiState.isWatching) {
                                        Icons.Default.Visibility
                                    } else {
                                        Icons.Default.VisibilityOff
                                    },
                                    contentDescription = if (uiState.isWatching) {
                                        stringResource(R.string.projects_object_unwatch)
                                    } else {
                                        stringResource(R.string.projects_object_watch)
                                    }
                                )
                            }

                            Box {
                                MochiIconButton(onClick = { showOverflow = true }) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = stringResource(
                                            MochiR.string.common_more_options
                                        )
                                    )
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
                                        leadingIcon = {
                                            Icon(Icons.Outlined.Delete, contentDescription = null)
                                        },
                                    )
                                }
                            }
                        }
                    )

                    SaveStatusIndicator(
                        status = uiState.saveStatus,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                    // The web's tabs, in its order and with its counts.
                    // Attachments and links fold into Properties as inline
                    // sections; watching is the eye icon in the header above.
                    val order = sheetTabs(merge = objClass?.requests.orEmpty().contains("merge"))
                    val format = LocalFormat.current
                    val labels = order.map { tab ->
                        when (tab) {
                            TAB_PROPERTIES -> stringResource(R.string.projects_object_tab_properties)
                            TAB_COMMENTS -> stringResource(
                                R.string.projects_object_tab_comments,
                                format.formatNumber(commentCount(uiState.comments)),
                            )
                            TAB_REQUESTS -> stringResource(
                                R.string.projects_object_tab_requests,
                                format.formatNumber(uiState.requests.size),
                            )
                            else -> stringResource(R.string.projects_object_tab_activity)
                        }
                    }
                    // A class that takes no merge requests has no such tab to stay on.
                    val shown = uiState.selectedTab.takeIf { tab -> tab in order } ?: TAB_PROPERTIES
                    MochiTabRow(
                        tabs = labels.map { title -> MochiTab(title) },
                        selectedIndex = order.indexOf(shown),
                        onSelect = { index -> viewModel.selectTab(order[index]) },
                        containerColor = Color.Transparent,
                        scrollable = true,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Tab content
                    when (shown) {
                        TAB_PROPERTIES -> PropertiesTab(
                            obj = obj,
                            projectDetails = projectDetails,
                            viewModel = viewModel,
                            onAddChild = { onAddChild(obj.id) },
                            onNavigateToObject = onNavigateToObject,
                            projectId = projectId,
                        )
                        TAB_COMMENTS -> CommentsTab(
                            comments = uiState.comments,
                            projectId = projectId,
                            drafts = viewModel.drafts,
                            target = obj.id,
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
                                "/projects/$projectId/-/comment/${comment.id}/asset/avatar"
                            }
                        )
                        TAB_ACTIVITY -> ActivityTab(
                            activity = uiState.activity,
                            projectDetails = projectDetails,
                            avatarUrlBuilder = { entry ->
                                "/projects/$projectId/-/activity/${entry.id}/asset/avatar"
                            }
                        )
                        TAB_REQUESTS -> RequestsTab(
                            requests = uiState.requests,
                            projectId = projectId,
                            viewModel = viewModel,
                            onViewDiff = onViewDiff
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        MochiAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = stringResource(R.string.projects_object_delete_title),
            text = stringResource(R.string.projects_object_delete_message),
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

/** The sheet's tabs, by the view model's numbers for them. */
internal const val TAB_PROPERTIES = 0
internal const val TAB_COMMENTS = 1
internal const val TAB_ACTIVITY = 2
internal const val TAB_REQUESTS = 3

/**
 * The sheet's tabs in the web's order: properties, comments, the merge
 * requests of a class that takes them ([merge]), and activity last.
 */
internal fun sheetTabs(merge: Boolean): List<Int> = buildList {
    add(TAB_PROPERTIES)
    add(TAB_COMMENTS)
    if (merge) add(TAB_REQUESTS)
    add(TAB_ACTIVITY)
}

/** Every comment in a thread, replies included, as the web's count has it. */
internal fun commentCount(comments: List<Comment>): Int =
    comments.sumOf { comment -> 1 + commentCount(comment.children) }
