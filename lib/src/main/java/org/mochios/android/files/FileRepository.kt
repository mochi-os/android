// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.files

import android.net.Uri
import java.io.File

/**
 * Base class giving every feature repository the same file operations.
 * ViewModels hand a [Uri] to their repository rather than reaching for
 * [FileStore], which stays protected; multipart building is left to subclasses.
 */
abstract class FileRepository(protected val fileStore: FileStore) {

    /**
     * Copies picked content into the cache, keeping real names and extensions.
     * All or nothing: one unreadable URI deletes what was staged and throws.
     * Callers must [discardStaged] the result.
     */
    suspend fun stageFiles(uris: List<Uri>, fallbackName: String = "file"): List<File> =
        fileStore.cacheFiles(uris, fallbackName)

    /** Copies one picked [uri] into the cache; null when it can't be opened. */
    suspend fun stageFile(uri: Uri, fallbackName: String = "file"): File? =
        fileStore.cacheFile(uri, fallbackName)

    /** Deletes temp files produced by [stageFiles] or [stageFile]. */
    suspend fun discardStaged(files: List<File>) {
        fileStore.deleteAll(files)
    }

    suspend fun readTextFile(uri: Uri): String? =
        fileStore.readText(uri)

    /**
     * Reads a picked document, unwrapping a zipped export. Use this for any
     * backup read-back.
     */
    suspend fun readTextOrZippedFile(uri: Uri): String? =
        fileStore.readTextOrZipped(uri)

    suspend fun saveTextFile(uri: Uri, text: String): Boolean =
        fileStore.writeText(uri, text)

    /** The picked file's real name, for labelling it back to the user. */
    suspend fun fileName(uri: Uri, fallback: String = ""): String =
        fileStore.displayName(uri, fallback)

    /** Suggested save-dialog name for an export of [kind] taken from [subject]. */
    fun exportFileName(subject: String?, kind: String, extension: String = "json"): String =
        fileStore.exportFileName(subject, kind, extension)

    /**
     * Suggested save-dialog name that keeps [subject] as the user wrote it,
     * e.g. `Crm Testing.csv`. For an export they open rather than archive.
     */
    fun exportDisplayName(subject: String?, extension: String): String =
        fileStore.exportDisplayName(subject, extension)
}
