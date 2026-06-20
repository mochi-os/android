// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.mochios.market.R
import org.mochios.market.model.Photo

/**
 * Listing photo manager. Grid of square thumbnails with overlaid delete and
 * reorder controls. Mirrors the web `photo-manager.tsx` flow.
 *
 * Reorder uses up/down arrow buttons rather than the experimental
 * drag-and-drop because the listing editor on touch screens with thumbnails
 * smaller than the drag threshold is awkward to scrub. The arrow buttons
 * preserve the swap-with-neighbour semantics and remain accessible.
 *
 * @param photos        Current photo list (rank ascending).
 * @param onUpload      Invoked with selected URIs from [ActivityResultContracts.GetMultipleContents].
 * @param onDelete      Invoked with the comptroller-issued photo id.
 * @param onReorder     Invoked with the photos in the requested new order.
 * @param isUploading   True while a file upload is in flight; shows the
 *                      progress indicator under the grid.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoManager(
    photos: List<Photo>,
    onUpload: (List<Uri>) -> Unit,
    onDelete: (String) -> Unit,
    onReorder: (List<Photo>) -> Unit,
    isUploading: Boolean,
    modifier: Modifier = Modifier,
) {
    val pickPhotos = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        if (uris.isNotEmpty()) onUpload(uris)
    }

    // Drag-and-drop file upload — accepts images dragged from external apps
    // (Photos, Files, Drive). Mirrors the wikis `AttachmentsScreen` target
    // pattern, so the seller can drop a batch onto the grid instead of opening
    // the picker. Visible on Chromebooks, foldables, and Samsung DeX.
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

    androidx.compose.foundation.layout.Column(
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
    ) {
        if (photos.isEmpty()) {
            EmptyPhotos(onAdd = { pickPhotos.launch("image/*") })
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp),
            ) {
                items(photos, key = { it.id }) { photo ->
                    val index = photos.indexOf(photo)
                    PhotoCell(
                        photo = photo,
                        canMoveUp = index > 0,
                        canMoveDown = index < photos.size - 1,
                        onDelete = { onDelete(photo.id) },
                        onMoveUp = {
                            if (index > 0) {
                                val mut = photos.toMutableList()
                                mut.removeAt(index)
                                mut.add(index - 1, photo)
                                onReorder(mut)
                            }
                        },
                        onMoveDown = {
                            if (index < photos.size - 1) {
                                val mut = photos.toMutableList()
                                mut.removeAt(index)
                                mut.add(index + 1, photo)
                                onReorder(mut)
                            }
                        },
                    )
                }
                item {
                    AddPhotoTile(onAdd = { pickPhotos.launch("image/*") })
                }
            }
        }

        if (isUploading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.market_editor_photo_uploading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun EmptyPhotos(onAdd: () -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.market_editor_photos_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        OutlinedButton(onClick = onAdd) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(start = 6.dp))
            Text(stringResource(R.string.market_editor_photos_add))
        }
    }
}

@Composable
private fun PhotoCell(
    photo: Photo,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = "/market/-/photo/${photo.id}/thumbnail",
                contentDescription = photo.name,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // Delete overlay (top-right)
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(28.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        shape = androidx.compose.foundation.shape.CircleShape,
                    ),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.market_editor_photo_delete),
                    modifier = Modifier.size(16.dp),
                )
            }

            // Reorder overlay (bottom)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AddPhotoTile(onAdd: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onAdd),
    ) {
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.market_editor_photos_add),
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.market_editor_photos_add),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
