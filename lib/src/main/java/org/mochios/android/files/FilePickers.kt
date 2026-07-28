// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.files

import android.net.Uri
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

/**
 * An export that has been fetched but still needs somewhere to go.
 *
 * Held in ViewModel state between the export call returning and the user
 * picking a destination, so nothing large sits in composition.
 *
 * @property json the payload to write.
 * @property suggestedName name to offer in the system save dialog.
 */
data class PendingExport(val json: String, val suggestedName: String)

/** Opens the system save dialog. See [rememberFileSaveLauncher]. */
fun interface FileSaveLauncher {

    /** Asks the user where to put a file, offering [fileName] as its name. */
    fun launch(fileName: String)
}

/**
 * Remembers a save dialog for JSON documents.
 *
 * Only the destination comes back through here — [onResult] hands the uri
 * straight to the caller, who writes to it through `FileRepository`. Nothing
 * is held in composition, so an export interrupted by process death loses
 * nothing that wasn't already lost.
 *
 * @param onResult called with the chosen destination, or with null when the
 *   user backs out. Callers must handle the null case: leaving a cancelled
 *   export pending would stall the next one.
 */
@Composable
fun rememberFileSaveLauncher(onResult: (Uri?) -> Unit): FileSaveLauncher {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        onResult(uri)
    }
    return remember(launcher) {
        FileSaveLauncher { fileName -> launcher.launch(fileName) }
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
