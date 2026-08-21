// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.`object`

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.model.Attachment
import org.mochios.android.ui.components.AttachmentGallery
import org.mochios.crm.R

/**
 * Inline attachments section inside PropertiesTab, matching the web's
 * object-detail-panel (no separate tab).
 */
@Composable
fun AttachmentsSection(
    attachments: List<Attachment>,
    crmId: String,
    onAddAttachment: (Uri) -> Unit,
    onDeleteAttachment: (String) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Attachment?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onAddAttachment(uri)
        }
    }

    val urlBuilder: (Attachment) -> String = { attachment ->
        "/crm/$crmId/-/attachments/${attachment.id}"
    }
    val thumbnailUrlBuilder: (Attachment) -> String = { attachment ->
        "/crm/$crmId/-/attachments/${attachment.id}/thumbnail"
    }

    // Newest first.
    val ordered = remember(attachments) {
        attachments.sortedByDescending { attachment -> attachment.created }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.crm_attachments),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { filePicker.launch("*/*") }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.crm_attachment_add))
            }
        }

        if (ordered.isEmpty()) {
            Text(
                stringResource(R.string.crm_attachment_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AttachmentGallery(
                attachments = ordered,
                urlBuilder = urlBuilder,
                thumbnailUrlBuilder = thumbnailUrlBuilder,
                onDelete = { attachment -> pendingDelete = attachment },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
        }
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.crm_attachment_delete_confirm_title),
            message = stringResource(R.string.crm_attachment_delete_confirm_message),
            onConfirm = {
                onDeleteAttachment(toDelete.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}
