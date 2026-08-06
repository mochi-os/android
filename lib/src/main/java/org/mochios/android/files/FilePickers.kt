// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.files

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
 * The one piece of file handling that has to stay in the UI: driving the
 * system pickers. Only the composition can reach the activity-result
 * registry, so a ViewModel can't open a picker itself.
 *
 * Everything after the pick — reading, writing, caching, uploading — belongs
 * to `FileRepository`, which ViewModels inject. These helpers deliberately
 * hand back nothing but a [Uri].
 */

/** Mime type of a JSON export, and the save dialog's default. */
const val MIME_JSON = "application/json"

/** Mime type of a CSV export. */
const val MIME_CSV = "text/csv"

/**
 * An export that has been fetched but still needs somewhere to go.
 *
 * Held in ViewModel state between the export call returning and the user
 * picking a destination, so nothing large sits in composition.
 *
 * @property content the payload to write.
 * @property suggestedName name to offer in the system save dialog.
 * @property mimeType what the payload is, so a screen offering more than one
 *   kind of export knows which save dialog to open.
 */
data class PendingExport(
    val content: String,
    val suggestedName: String,
    val mimeType: String = MIME_JSON,
)

/** Opens the system save dialog. See [rememberFileSaveLauncher]. */
fun interface FileSaveLauncher {

    /** Asks the user where to put a file, offering [fileName] as its name. */
    fun launch(fileName: String)
}

/**
 * Remembers a save dialog for documents of [mimeType].
 *
 * Only the destination comes back through here — [onResult] hands the uri
 * straight to the caller, who writes to it through `FileRepository`. Nothing
 * is held in composition, so an export interrupted by process death loses
 * nothing that wasn't already lost.
 *
 * The type is fixed when the launcher is remembered, so a screen that exports
 * both JSON and CSV remembers one launcher per type. The dialog is hinted to
 * open on Downloads — see [CreateDocumentInDownloads] for how far that goes.
 *
 * @param mimeType what the file will hold — [MIME_JSON] or [MIME_CSV].
 * @param onResult called with the chosen destination, or with null when the
 *   user backs out. Callers must handle the null case: leaving a cancelled
 *   export pending would stall the next one.
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
 * `CreateDocument` that opens on Downloads.
 *
 * The base contract sends only the suggested name, so the dialog lands
 * wherever the picker was last left — which for an export is rarely where the
 * user wants it. `EXTRA_INITIAL_URI` is a hint, not a setting: a picker that
 * remembers its own last location, or an OEM picker that ignores the extra,
 * still decides. The user can navigate anywhere from there either way.
 */
private class CreateDocumentInDownloads(
    mimeType: String,
) : ActivityResultContracts.CreateDocument(mimeType) {

    override fun createIntent(context: Context, input: String): Intent {
        val intent = super.createIntent(context, input)
        downloadsUri()?.let { uri -> intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri) }
        return intent
    }

    /**
     * The Downloads folder as a documents-provider uri.
     *
     * Only meaningful from API 26, where the external storage provider exposes
     * it under a stable document id; below that the hint is skipped and the
     * picker opens wherever it would have.
     */
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
 * The display name for a picked [uri], for labelling it in a draft chip.
 *
 * Resolving a name is a `ContentResolver` query, so it can't happen inline
 * during composition — [resolve] is a suspending call the caller routes to its
 * repository, and the label fills in once it returns.
 *
 * @param resolve looks the name up, e.g. `viewModel::fileName`.
 * @param fallback shown until the real name arrives.
 * @return the file's name, or [fallback] while the lookup is in flight.
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
