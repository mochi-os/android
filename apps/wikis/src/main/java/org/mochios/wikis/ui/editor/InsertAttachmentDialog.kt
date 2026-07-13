// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.mochios.wikis.R
import org.mochios.wikis.model.Attachment
import org.mochios.wikis.ui.components.LocalWikiContext

/**
 * Insert-attachment picker dialog. Lists the page's existing attachments
 * (4-column grid, thumbnails for images, file icon otherwise) and exposes
 * a "Upload new" trigger that opens the system multi-file picker.
 *
 * Reuses [PageEditorViewModel] because the editor already owns the wiki id,
 * slug, and attachment list. Tapping a tile builds a markdown snippet — an
 * image attachment becomes `![name](attachments/<id>/thumbnail)`, anything
 * else becomes `[name](attachments/<id>)` — and asks the ViewModel to
 * splice it in at [cursor]. The new cursor position is reported back to
 * the screen via [onInserted] so the body field's selection can move past
 * the inserted text.
 *
 * Mirrors the dialog body inside `apps/wikis/web/src/features/wiki/page-editor.tsx`.
 */
@Composable
fun InsertAttachmentDialog(
    open: Boolean,
    viewModel: PageEditorViewModel,
    cursor: Int,
    onDismiss: () -> Unit,
    onInserted: (newCursor: Int) -> Unit,
) {
    if (!open) return
    val state by viewModel.uiState.collectAsState()
    val wiki = LocalWikiContext.current
    val context = LocalContext.current

    val uploadFailedMsg = stringResource(R.string.wikis_insert_dialog_error)

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.uploadAttachments(
                uris = uris,
                contentResolver = context.contentResolver,
                cacheDir = context.cacheDir,
                uploadFailed = uploadFailedMsg,
            )
        }
    }

    // Fetch the attachment list each time the dialog opens — the user may
    // have come back from the Attachments screen after deleting one.
    LaunchedEffect(open) {
        if (open) viewModel.loadAttachments()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wikis_insert_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.wikis_insert_dialog_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { filePicker.launch("*/*") },
                    enabled = !state.isUploading,
                ) {
                    if (state.isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Box(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.wikis_insert_dialog_upload_new))
                }

                Box(modifier = Modifier.height(12.dp))

                when {
                    state.isAttachmentsLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }
                    state.attachments.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.wikis_insert_dialog_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp),
                        ) {
                            items(state.attachments, key = { it.id }) { attachment ->
                                AttachmentTile(
                                    attachment = attachment,
                                    thumbnailUrl = wiki?.let { ctx ->
                                        "${ctx.baseURL}attachments/${attachment.id}/thumbnail"
                                    },
                                    onClick = {
                                        val snippet = buildSnippet(attachment)
                                        val nextCursor = viewModel.insertAtCursor(snippet, cursor)
                                        onInserted(nextCursor)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(org.mochios.android.R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun AttachmentTile(
    attachment: Attachment,
    thumbnailUrl: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (attachment.isImage && thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = attachment.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.AttachFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Box(modifier = Modifier.height(4.dp))
        Text(
            text = attachment.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Convenience extension matching the web `isImage(attachment.type)` helper. */
private val Attachment.isImage: Boolean get() = type.startsWith("image/")

/** Build the markdown snippet to splice into the body for the picked attachment. */
private fun buildSnippet(attachment: Attachment): String {
    val url = "attachments/${attachment.id}"
    return if (attachment.isImage) {
        "![${attachment.name}]($url/thumbnail)"
    } else {
        "[${attachment.name}]($url)"
    }
}
