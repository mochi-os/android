package org.mochios.market.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mochios.android.i18n.LocalFormat
import org.mochios.market.R
import org.mochios.market.model.Asset

/**
 * Digital-asset manager. Same upload + reorder UX as [PhotoManager] but for
 * arbitrary file types (zip, pdf, mp3, …) plus an inline "External URL" form
 * for assets hosted off-platform. Mirrors `digital-assets-manager.tsx` on web.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DigitalAssetsManager(
    assets: List<Asset>,
    onUpload: (List<Uri>) -> Unit,
    onDelete: (Long) -> Unit,
    onReorder: (List<Asset>) -> Unit,
    onAddExternal: (filename: String, mime: String, reference: String) -> Unit,
    isUploading: Boolean,
    modifier: Modifier = Modifier,
) {
    val pickFiles = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        if (uris.isNotEmpty()) onUpload(uris)
    }

    // Drag-and-drop file upload — accepts files dragged from external apps.
    // Mirrors the wikis `AttachmentsScreen` target so the seller can drop a
    // batch onto the asset list instead of opening the picker.
    var isDraggedOver by remember { mutableStateOf(false) }
    val dragDropTarget = remember(onUpload) {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                isDraggedOver = true
            }
            override fun onEnded(event: DragAndDropEvent) {
                isDraggedOver = false
            }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val clip = event.toAndroidDragEvent().clipData ?: return false
                val uris = mutableListOf<Uri>()
                for (i in 0 until clip.itemCount) {
                    val u = clip.getItemAt(i).uri ?: continue
                    uris.add(u)
                }
                if (uris.isEmpty()) return false
                onUpload(uris)
                return true
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    val clip = event.toAndroidDragEvent().clipData
                    clip != null && (0 until clip.itemCount).any { i ->
                        clip.getItemAt(i).uri != null
                    }
                },
                target = dragDropTarget,
            )
            .then(
                if (isDraggedOver) Modifier.border(
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                ) else Modifier
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (assets.isEmpty()) {
            Text(
                text = stringResource(R.string.market_editor_assets_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                assets.forEachIndexed { index, asset ->
                    AssetRow(
                        asset = asset,
                        onDelete = { onDelete(asset.id) },
                    )
                    if (index < assets.size - 1) HorizontalDivider()
                }
            }
        }

        if (isUploading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        OutlinedButton(
            onClick = { pickFiles.launch("*/*") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.market_editor_asset_add))
        }

        ExternalAssetForm(onSubmit = onAddExternal)
    }
}

@Composable
private fun AssetRow(
    asset: Asset,
    onDelete: () -> Unit,
) {
    val format = LocalFormat.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (asset.hosting == "external") Icons.Default.Link
            else Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = asset.filename,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            val sizeLabel = if (asset.size > 0) format.formatFileSize(asset.size) else ""
            val mimeLabel = asset.mime
            val subtitle = listOf(mimeLabel, sizeLabel).filter { it.isNotEmpty() }.joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.market_editor_asset_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ExternalAssetForm(
    onSubmit: (filename: String, mime: String, reference: String) -> Unit,
) {
    var filename by remember { mutableStateOf("") }
    var mime by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.market_editor_asset_external),
            style = MaterialTheme.typography.titleSmall,
        )
        OutlinedTextField(
            value = filename,
            onValueChange = { filename = it },
            label = { Text(stringResource(R.string.market_editor_asset_external_filename)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = mime,
            onValueChange = { mime = it },
            label = { Text(stringResource(R.string.market_editor_asset_external_mime)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = reference,
            onValueChange = { reference = it },
            label = { Text(stringResource(R.string.market_editor_asset_external_url)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = {
                if (filename.isNotBlank() && reference.isNotBlank()) {
                    onSubmit(filename.trim(), mime.trim(), reference.trim())
                    filename = ""; mime = ""; reference = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.market_editor_asset_external_add))
        }
    }
}
