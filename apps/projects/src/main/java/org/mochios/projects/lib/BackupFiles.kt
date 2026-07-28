// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.lib

import android.content.Context
import android.net.Uri
import java.time.LocalDate

/**
 * Reading and writing the JSON blobs the project export/import flows hand to
 * the system file picker. Shared by the project data export on the project
 * screen and the design export/import on the design screen.
 */

/**
 * Names an export after the project it came from, e.g.
 * `acme-design-2026-07-28.json`.
 *
 * @param projectName project the export belongs to; blank or null falls back
 *   to `unknown`.
 * @param kind what the file holds — `design` or `projects-backup`.
 * @return the suggested file name shown in the system save dialog.
 */
fun backupFileName(projectName: String?, kind: String): String {
    val slug = projectName
        ?.lowercase()
        ?.replace(Regex("[^a-z0-9]+"), "-")
        ?.trim('-')
        ?.takeIf { slug -> slug.isNotEmpty() }
        ?: "unknown"
    return "$slug-$kind-${LocalDate.now()}.json"
}

/**
 * Writes [text] to the document the user picked.
 *
 * @return true when the whole write went through, false on any I/O failure.
 */
fun writeTextToUri(context: Context, uri: Uri, text: String): Boolean {
    return try {
        val stream = context.contentResolver.openOutputStream(uri) ?: return false
        stream.use { output -> output.write(text.toByteArray()) }
        true
    } catch (_: Exception) {
        false
    }
}

/**
 * Reads the document the user picked.
 *
 * @return its contents, or null when the file can't be opened or read.
 */
fun readTextFromUri(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { reader -> reader.readText() }
    } catch (_: Exception) {
        null
    }
}
