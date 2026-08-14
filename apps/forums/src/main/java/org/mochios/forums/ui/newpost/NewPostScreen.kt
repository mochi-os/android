// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.newpost

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.model.Attachment
import org.mochios.android.ui.components.AttachmentCaptionDialog
import org.mochios.android.ui.components.MentionTextField
import org.mochios.android.files.rememberFileLabel
import org.mochios.forums.R
import org.mochios.android.R as MochiR

/**
 * Compose or edit a forum post: title, a markdown body with `@mention`
 * autocomplete, and file attachments. A single screen serves both — it is in
 * edit mode when the back-stack carries a `postId`, prefilling the fields and
 * showing the post's current attachments as keep/remove chips. Mirrors feeds'
 * `CreatePostScreen` — the submit action lives in the top bar and the
 * attachment chips can be reordered, since the server keeps the upload order.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewPostScreen(
    onBack: () -> Unit,
    onPostCreated: (String, String) -> Unit,
    viewModel: NewPostViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val attachments by viewModel.attachments.collectAsState()
    val existingAttachments by viewModel.existingAttachments.collectAsState()
    val removedExistingIds by viewModel.removedExistingIds.collectAsState()
    val newCaptions by viewModel.newCaptions.collectAsState()
    val existingCaptions by viewModel.existingCaptions.collectAsState()
    val title by viewModel.title.collectAsState()
    val body by viewModel.body.collectAsState()
    val isEditing = viewModel.isEditing

    // Which attachment's caption is being edited: a saved attachment's id or
    // a staged file's Uri, with the display name for the dialog. null = closed.
    var captioning by remember { mutableStateOf<CaptionTarget?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris -> viewModel.addAttachments(uris) }

    LaunchedEffect(uiState.postSuccess) {
        if (uiState.postSuccess) {
            onPostCreated(uiState.createdForum.ifBlank { viewModel.forumId }, uiState.createdPost)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (isEditing) R.string.forums_post_edit_title
                            else R.string.forums_new_post
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.submit() },
                        enabled = title.isNotBlank() && body.isNotBlank() && !uiState.isPosting
                    ) {
                        if (uiState.isPosting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                stringResource(
                                    if (isEditing) MochiR.string.common_save
                                    else R.string.forums_post_create_action
                                )
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { value -> viewModel.setTitle(value) },
                label = { Text(stringResource(R.string.forums_post_title_field)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.forums_post_body_field),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            MentionTextField(
                value = body,
                onValueChange = { value -> viewModel.setBody(value) },
                onSearch = { query -> viewModel.searchMembers(query) },
                placeholder = { Text(stringResource(R.string.forums_markdown_supported)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
                maxLines = 20,
                fillHeight = true
            )
            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = { filePickerLauncher.launch("*/*") },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.forums_post_attach))
            }

            if (existingAttachments.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                ExistingAttachmentChips(
                    attachments = existingAttachments,
                    removedIds = removedExistingIds,
                    captions = existingCaptions,
                    onMove = { id, direction -> viewModel.moveExistingAttachment(id, direction) },
                    onToggleRemove = { id -> viewModel.toggleRemoveExistingAttachment(id) },
                    onCaption = { attachment ->
                        captioning = CaptionTarget(
                            existingId = attachment.id,
                            uri = null,
                            name = attachment.name
                        )
                    },
                )
            }

            if (attachments.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                AttachmentChips(
                    attachments = attachments,
                    captions = newCaptions,
                    onMove = { uri, direction -> viewModel.moveAttachment(uri, direction) },
                    onRemove = { uri -> viewModel.removeAttachment(uri) },
                    onCaption = { uri, name ->
                        captioning = CaptionTarget(existingId = null, uri = uri, name = name)
                    },
                    resolveFileName = viewModel::fileName,
                )
            }

            uiState.error?.let { error ->
                Spacer(Modifier.height(12.dp))
                Text(
                    error.userMessage(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    captioning?.let { target ->
        AttachmentCaptionDialog(
            name = target.name,
            initial = target.existingId?.let { existingCaptions[it] }
                ?: target.uri?.let { newCaptions[it] }
                ?: "",
            onSave = { caption ->
                target.existingId?.let { viewModel.setExistingAttachmentCaption(it, caption) }
                target.uri?.let { viewModel.setAttachmentCaption(it, caption) }
                captioning = null
            },
            onDismiss = { captioning = null }
        )
    }
}

// Which attachment a caption edit addresses: a saved attachment by id, or a
// staged file by Uri, with the display name shown in the dialog.
private data class CaptionTarget(
    val existingId: String?,
    val uri: Uri?,
    val name: String,
)

/**
 * The provider's display name for [uri]. A `content://` path segment is an
 * opaque id, so the picker's chip would otherwise read "1000000042".
 */
@Composable
private fun rememberFileName(
    uri: Uri,
    fallback: String,
    resolve: suspend (Uri) -> String,
): String = rememberFileLabel(uri, resolve, fallback)

/** Picked files as removable chips, each with up/down controls when several. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttachmentChips(
    attachments: List<Uri>,
    captions: Map<Uri, String>,
    onMove: (Uri, Int) -> Unit,
    onRemove: (Uri) -> Unit,
    onCaption: (Uri, String) -> Unit,
    resolveFileName: suspend (Uri) -> String,
) {
    val fileLabel = stringResource(R.string.forums_attachment_file)
    val context = LocalContext.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        attachments.forEachIndexed { index, uri ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (attachments.size > 1) {
                    Column {
                        if (index > 0) {
                            IconButton(
                                onClick = { onMove(uri, -1) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.ExpandLess,
                                    contentDescription = stringResource(R.string.forums_attachment_move_up),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        if (index < attachments.lastIndex) {
                            IconButton(
                                onClick = { onMove(uri, 1) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.ExpandMore,
                                    contentDescription = stringResource(R.string.forums_attachment_move_down),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
                val name = rememberFileName(uri, fileLabel, resolveFileName)
                AssistChip(
                    onClick = { onRemove(uri) },
                    label = {
                        Text(
                            name.takeLast(25),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.forums_attachment_remove),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                )
                val isMedia = remember(uri) {
                    context.contentResolver.getType(uri)?.let {
                        it.startsWith("image/") || it.startsWith("video/")
                    } == true
                }
                if (isMedia) {
                    IconButton(
                        onClick = { onCaption(uri, name) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.ClosedCaption,
                            contentDescription = stringResource(
                                if ((captions[uri] ?: "").isEmpty())
                                    MochiR.string.attachment_caption_add
                                else MochiR.string.attachment_caption_edit
                            ),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * The post's current attachments while editing, as keep/remove chips. A
 * deselected chip is dropped on save; up/down controls reorder the kept set,
 * since the server keeps the order the ids arrive in.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExistingAttachmentChips(
    attachments: List<Attachment>,
    removedIds: Set<String>,
    captions: Map<String, String>,
    onMove: (String, Int) -> Unit,
    onToggleRemove: (String) -> Unit,
    onCaption: (Attachment) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        attachments.forEachIndexed { index, attachment ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (attachments.size > 1) {
                    Column {
                        if (index > 0) {
                            IconButton(
                                onClick = { onMove(attachment.id, -1) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.ExpandLess,
                                    contentDescription = stringResource(R.string.forums_attachment_move_up),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        if (index < attachments.lastIndex) {
                            IconButton(
                                onClick = { onMove(attachment.id, 1) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.ExpandMore,
                                    contentDescription = stringResource(R.string.forums_attachment_move_down),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
                FilterChip(
                    selected = attachment.id !in removedIds,
                    onClick = { onToggleRemove(attachment.id) },
                    label = {
                        Text(
                            attachment.name.takeLast(25),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.forums_attachment_remove),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                )
                if ((attachment.isImage || attachment.isVideo) && attachment.id !in removedIds) {
                    IconButton(
                        onClick = { onCaption(attachment) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.ClosedCaption,
                            contentDescription = stringResource(
                                if ((captions[attachment.id] ?: "").isEmpty())
                                    MochiR.string.attachment_caption_add
                                else MochiR.string.attachment_caption_edit
                            ),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
