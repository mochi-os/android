package org.mochios.market.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.android.i18n.LocalFormat
import org.mochios.market.R
import org.mochios.market.model.Asset

/**
 * Vertical list of digital assets attached to a listing. Each row shows
 * filename + human size; the trailing icon button fires [onDownload].
 *
 * Hidden when [assets] is empty so the caller can drop the surrounding
 * section heading.
 */
@Composable
fun DigitalAssetsList(
    assets: List<Asset>,
    modifier: Modifier = Modifier,
    onDownload: (Asset) -> Unit = {},
) {
    if (assets.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        assets.forEach { asset ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = asset.filename.ifBlank { "?" },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (asset.size > 0L) {
                        Text(
                            text = LocalFormat.current.formatFileSize(asset.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { onDownload(asset) }) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = stringResource(R.string.market_asset_download),
                    )
                }
            }
        }
    }
}
