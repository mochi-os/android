// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.files

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember

/**
 * Driving the system pickers has to stay in the UI: only a composition can
 * reach the activity-result registry. Everything after the pick - reading,
 * writing, caching, uploading - belongs to `FileRepository`.
 */

/** Mime type of a JSON export, and the save dialog's default. */
const val MIME_JSON = "application/json"

/** Mime type of a CSV export. */
const val MIME_CSV = "text/csv"

/** Mime type of a zipped export. */
const val MIME_ZIP = "application/zip"

/**
 * An export waiting for a destination. A null [content] means the server holds
 * the payload and it is streamed to the destination rather than parked here.
 */
data class PendingExport(
    val suggestedName: String,
    val mimeType: String = MIME_JSON,
    val content: String? = null,
)

data class SavedExport(
    val uri: Uri,
    val mimeType: String,
    val name: String,
)

/**
 * Opens the share sheet with a finished export. The read grant travels both as
 * a flag and as [ClipData], which is what makes it stick on targets that ignore
 * `EXTRA_STREAM` alone.
 */
fun shareExportFile(context: Context, export: SavedExport) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = export.mimeType
        putExtra(Intent.EXTRA_STREAM, export.uri)
        // Names the sheet's content preview; without it the sheet reads as a
        // bare file with no title.
        putExtra(Intent.EXTRA_TITLE, export.name)
        clipData = ClipData.newRawUri(export.name, export.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, export.name)
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(chooser)
}

/** Opens the system save dialog. See [rememberFileSaveLauncher]. */
fun interface FileSaveLauncher {

    /** Asks the user where to put a file, offering [fileName] as its name. */
    fun launch(fileName: String)
}

/**
 * Remembers a save dialog for [mimeType]. The type is fixed when remembered, so
 * a screen exporting both JSON and CSV remembers one launcher per type.
 * [onResult] gets null when the user backs out, and callers must handle it.
 */
@Composable
fun rememberFileSaveLauncher(
    mimeType: String = MIME_JSON,
    onResult: (Uri?) -> Unit,
): FileSaveLauncher {
    val contract = remember(mimeType) { CreateDocumentInDownloads(mimeType) }
    val launcher = rememberLauncherForActivityResult(contract) { uri: Uri? ->
        onResult(uri)
    }
    return remember(launcher) {
        FileSaveLauncher { fileName -> launcher.launch(fileName) }
    }
}

/**
 * `CreateDocument` that opens on Downloads. `EXTRA_INITIAL_URI` is a hint: a
 * picker that remembers its own last location, or an OEM one, still decides.
 */
private class CreateDocumentInDownloads(
    mimeType: String,
) : ActivityResultContracts.CreateDocument(mimeType) {

    override fun createIntent(context: Context, input: String): Intent {
        val intent = super.createIntent(context, input)
        downloadsUri()?.let { uri -> intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri) }
        return intent
    }

    private fun downloadsUri(): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return null
        }
        return DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:${Environment.DIRECTORY_DOWNLOADS}",
        )
    }
}

/**
 * The display name for a picked [uri]; [resolve] is a suspending lookup, so
 * [fallback] shows until it returns.
 */
@Composable
fun rememberFileLabel(
    uri: Uri,
    resolve: suspend (Uri) -> String,
    fallback: String = "",
): String {
    val label by produceState(initialValue = fallback, uri) {
        value = resolve(uri)
    }
    return label
}
