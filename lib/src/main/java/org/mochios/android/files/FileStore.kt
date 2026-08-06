// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Everything the app does with files: reading and writing documents the system
 * picker hands back, staging picked content in the cache, and building the
 * multipart parts an upload is sent as. Backed by the platform
 * `ContentResolver` and the app cache dir.
 *
 * This is device plumbing, not a data source. Feature repositories depend on
 * it the way they depend on an API service, and they are the only things that
 * do — a ViewModel asks its own repository to save or read a file rather than
 * reaching for the store itself. [FileRepository] is the base class that hands
 * every feature repository the same file-facing surface.
 *
 * Every upload should go through here so each part carries the real filename
 * and MIME type: the server keeps both, and dropping them (e.g. a blanket
 * [DEFAULT_MIME], or a content-uri document id used as the name) is what
 * leaves an attachment unrecognisable and un-previewable later.
 *
 * The I/O calls suspend onto [Dispatchers.IO]. That matters most on the write
 * and cache paths: an export or a picked video runs to megabytes, and a
 * picker's callback lands on the main thread, so doing the work there stutters
 * the UI. Building a multipart part does no I/O — the body streams off disk
 * when the request is sent — so those stay plain calls.
 *
 * Picking the [Uri] stays in the UI, since only the composition can drive the
 * activity-result registry. Everything after the pick belongs here.
 */
@Singleton
class FileStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // ---- Documents ----

    /**
     * Reads the document at [uri] as text.
     *
     * @return its contents, or null when the uri can't be opened or read.
     */
    suspend fun readText(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { reader -> reader.readText() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Writes [text] to the document at [uri], replacing whatever is there.
     *
     * @return true when the whole write went through, false on any I/O failure.
     */
    suspend fun writeText(uri: Uri, text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openOutputStream(uri)
                ?: return@withContext false
            stream.use { output -> output.write(text.toByteArray()) }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Names an export after the thing it came from, e.g.
     * `acme-design-2026-07-28.json`.
     *
     * @param subject what the export belongs to; blank or null falls back to
     *   `unknown`.
     * @param kind what the file holds — `design`, `projects-backup`, ...
     * @param extension the file's extension, without the dot.
     * @param date stamped into the name; defaults to today.
     * @return the suggested file name for the system save dialog.
     */
    fun exportFileName(
        subject: String?,
        kind: String,
        extension: String = "json",
        date: LocalDate = LocalDate.now()
    ): String {
        val slug = subject
            ?.lowercase()
            ?.replace(Regex("[^a-z0-9]+"), "-")
            ?.trim('-')
            ?.takeIf { value -> value.isNotEmpty() }
            ?: "unknown"
        return "$slug-$kind-$date.$extension"
    }

    // ---- Names and types ----

    /**
     * Resolves a picked content [uri] to a real filename with an extension:
     * prefers the provider's display name, falls back to the last path segment
     * then [fallback], and appends an extension from the MIME type when
     * missing. `:` and `/` are stripped so the result is a valid cache filename.
     */
    suspend fun displayName(uri: Uri, fallback: String = "file"): String =
        withContext(Dispatchers.IO) { resolveName(uri, fallback) }

    /** MIME type the provider reports for [uri]; [DEFAULT_MIME] when unknown. */
    suspend fun mimeType(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.getType(uri) ?: DEFAULT_MIME
    }

    /** MIME type for [file], from its extension; [DEFAULT_MIME] when unknown. */
    fun mimeType(file: File): String {
        val ext = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: SUPPLEMENTAL_MIME_BY_EXT[ext]
            ?: DEFAULT_MIME
    }

    // ---- Cache staging ----

    /**
     * Copies the content behind [uri] into the app cache, named via
     * [displayName] so it keeps a real filename and extension.
     *
     * This is the one copy every attachment picker should use: a hand-rolled
     * copy that names the temp file from the uri's document id loses the
     * extension, which then makes the upload's MIME type degrade to
     * [DEFAULT_MIME].
     *
     * @return the temp file, or null when the uri can't be opened.
     */
    suspend fun cacheFile(uri: Uri, fallbackName: String = "file"): File? =
        withContext(Dispatchers.IO) { copyToCache(uri, fallbackName) }

    /**
     * Copies each of [uris] into the app cache via [cacheFile], preserving
     * order. Callers own the returned files and must [deleteAll] them once the
     * upload finishes. This is the streaming counterpart to reading each uri
     * into memory — the request bodies built from these files upload straight
     * off disk and survive request retries.
     *
     * @throws IOException when a uri can't be opened, so a picked attachment is
     * never silently dropped from the upload; files cached before the failure
     * are cleaned up.
     */
    suspend fun cacheFiles(uris: List<Uri>, fallbackName: String = "file"): List<File> =
        withContext(Dispatchers.IO) {
            val files = mutableListOf<File>()
            for (uri in uris) {
                val file = copyToCache(uri, fallbackName)
                if (file == null) {
                    files.forEach { cached -> cached.delete() }
                    throw IOException("Cannot read $uri")
                }
                files.add(file)
            }
            files
        }

    /** Deletes each temp [files], ignoring individual failures. */
    suspend fun deleteAll(files: List<File>) {
        withContext(Dispatchers.IO) {
            files.forEach { file -> file.delete() }
        }
    }

    // ---- Multipart ----

    /**
     * A multipart part built from [file] under form field [field], carrying the
     * file's name and MIME type.
     *
     * @param mimeType overrides the type inferred from the file's extension,
     *   for when the server requires a specific one regardless of the file
     *   (e.g. a re-encoded JPEG upload); null infers it.
     */
    fun filePart(field: String, file: File, mimeType: String? = null): MultipartBody.Part {
        val type = mimeType ?: mimeType(file)
        val body = file.asRequestBody(type.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(field, file.name, body)
    }

    /** [filePart] for each of [files], under the same form [field]. */
    fun fileParts(field: String, files: List<File>): List<MultipartBody.Part> =
        files.map { file -> filePart(field, file) }

    /**
     * A multipart part from in-memory [bytes] under form field [field],
     * carrying [fileName] and [mimeType]. For callers that already hold the
     * bytes and type (no [File] or content [Uri] to resolve from).
     */
    fun bytesPart(
        field: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): MultipartBody.Part =
        MultipartBody.Part.createFormData(
            field,
            fileName,
            bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        )

    // ---- Internals ----

    private fun resolveName(uri: Uri, fallback: String): String {
        val resolver = context.contentResolver
        val displayName = resolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        var name = (displayName ?: uri.lastPathSegment ?: fallback)
            .substringAfterLast('/')
            .replace(':', '_')
        if (!name.contains('.')) {
            val mime = resolver.getType(uri)
            val ext = mime?.let { type -> MimeTypeMap.getSingleton().getExtensionFromMimeType(type) }
                ?: mime?.let { type -> SUPPLEMENTAL_EXT_BY_MIME[type] }
            if (!ext.isNullOrBlank()) {
                name = "$name.$ext"
            }
        }
        return name.ifBlank { fallback }
    }

    private fun copyToCache(uri: Uri, fallbackName: String): File? {
        val resolver = context.contentResolver
        // Two picked files can share a display name (and a crashed upload can
        // leave a stale copy behind); writing to the same path would upload one
        // file's bytes twice. Disambiguate with a counter before the extension.
        var target = File(context.cacheDir, resolveName(uri, fallbackName))
        var counter = 2
        while (target.exists()) {
            val extension = target.extension
            val stem = target.nameWithoutExtension
            val name = if (extension.isEmpty()) "$stem-$counter" else "$stem-$counter.$extension"
            target = File(context.cacheDir, name)
            counter++
        }
        return resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
            target
        }
    }

    companion object {

        /** Fallback content type when a real MIME type can't be determined. */
        const val DEFAULT_MIME = "application/octet-stream"

        // Types Android's MimeTypeMap commonly omits (mainly Office formats).
        // Kept here so a File-based upload keeps the right type instead of
        // degrading to application/octet-stream when the extension round-trips
        // through MimeTypeMap.
        private val SUPPLEMENTAL_MIME_BY_EXT = mapOf(
            "doc" to "application/msword",
            "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xls" to "application/vnd.ms-excel",
            "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "ppt" to "application/vnd.ms-powerpoint",
            "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "csv" to "text/csv",
        )
        private val SUPPLEMENTAL_EXT_BY_MIME =
            SUPPLEMENTAL_MIME_BY_EXT.entries.associate { (ext, mime) -> mime to ext }
    }
}
