// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.files

import android.net.Uri
import java.io.File

/**
 * Base class for feature repositories that handle files, giving every one of
 * them the same set of file operations from a single definition.
 *
 * A ViewModel picks a [Uri] in the UI and hands it to its own repository —
 * it never reaches for a [FileStore] itself. This class is what makes that
 * cheap: subclasses inherit the whole file-facing surface instead of writing
 * their own passthroughs, and the store stays `protected` so it doesn't leak
 * past the repository layer.
 *
 * Deliberately narrow. Multipart building is an implementation detail of an
 * upload, so it isn't exposed here — subclasses reach [fileStore] directly for
 * `filePart` and friends when assembling a request.
 *
 * @param fileStore device plumbing every subclass shares.
 */
abstract class FileRepository(protected val fileStore: FileStore) {

    /**
     * Copies picked content into the cache so it can be uploaded straight off
     * disk, keeping each file's real name and extension.
     *
     * All or nothing: one unreadable URI deletes everything already staged and
     * throws, so the result always holds a file for every entry in [uris].
     * Callers own the result and must [discardStaged] it once the upload
     * finishes.
     *
     * @throws java.io.IOException when any [uris] entry can't be opened.
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

    /**
     * Reads a text document the user picked.
     *
     * @return its contents, or null when the file can't be read.
     */
    suspend fun readTextFile(uri: Uri): String? =
        fileStore.readText(uri)

    /**
     * Reads a document the user picked, unwrapping it when it is a zipped
     * export rather than the bare file. Use this wherever a backup is read
     * back: the user has both to hand and no reason to know which we want.
     *
     * @return its contents, or null when the file can't be read.
     */
    suspend fun readTextOrZippedFile(uri: Uri): String? =
        fileStore.readTextOrZipped(uri)

    /**
     * Writes [text] to the document the user picked.
     *
     * @return true when the file was written in full.
     */
    suspend fun saveTextFile(uri: Uri, text: String): Boolean =
        fileStore.writeText(uri, text)

    /**
     * Writes [text] to the document the user picked as a zip, under a single
     * entry named [entryName].
     *
     * @return true when the file was written in full.
     */
    suspend fun saveZipFile(uri: Uri, entryName: String, text: String): Boolean =
        fileStore.writeZip(uri, entryName, text)

    /** The picked file's real name, for labelling it back to the user. */
    suspend fun fileName(uri: Uri, fallback: String = ""): String =
        fileStore.displayName(uri, fallback)

    /** Suggested save-dialog name for an export of [kind] taken from [subject]. */
    fun exportFileName(subject: String?, kind: String, extension: String = "json"): String =
        fileStore.exportFileName(subject, kind, extension)
}
