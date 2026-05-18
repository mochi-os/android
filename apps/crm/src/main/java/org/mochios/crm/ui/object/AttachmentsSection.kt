package org.mochios.crm.ui.`object`

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.model.Attachment
import org.mochios.android.ui.components.AttachmentGallery
import org.mochios.crm.R
import java.io.File
import org.mochios.android.R as MochiR

/**
 * Inline attachments section, rendered inside PropertiesTab to match the
 * web's object-detail-panel layout (attachments embedded in Properties,
 * not a separate tab).
 */
@Composable
fun AttachmentsSection(
    attachments: List<Attachment>,
    crmId: String,
    serverUrl: String,
    onAddAttachment: (File) -> Unit,
    onDeleteAttachment: (String) -> Unit,
) {
    val context = LocalContext.current
    val defaultName = stringResource(R.string.crm_attachment_default_name)
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val fileName = uri.lastPathSegment ?: defaultName
                val tempFile = File(context.cacheDir, fileName)
                tempFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
                inputStream.close()
                onAddAttachment(tempFile)
            }
        }
    }

    val urlBuilder: (Attachment) -> String = { attachment ->
        "$serverUrl/crm/$crmId/-/attachments/${attachment.id}"
    }
    val thumbnailUrlBuilder: (Attachment) -> String = { attachment ->
        "$serverUrl/crm/$crmId/-/attachments/${attachment.id}/thumbnail"
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

        if (attachments.isEmpty()) {
            Text(
                stringResource(R.string.crm_attachment_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AttachmentGallery(
                attachments = attachments,
                urlBuilder = urlBuilder,
                thumbnailUrlBuilder = thumbnailUrlBuilder,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                attachments.forEach { attachment ->
                    AttachmentRow(
                        attachment = attachment,
                        onDelete = { onDeleteAttachment(attachment.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun AttachmentRow(
    attachment: Attachment,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when {
            attachment.isImage -> Icons.Default.Image
            attachment.isVideo -> Icons.Default.VideoFile
            else -> Icons.Default.AttachFile
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = LocalFormat.current.formatFileSize(attachment.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(MochiR.string.common_delete),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
