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
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Documents, cache staging and multipart building over `ContentResolver`. Every
 * upload goes through here so each part keeps the real filename and MIME type:
 * the server stores both, and losing them leaves an attachment unrecognisable.
 */
@Singleton
class FileStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    // ---- Documents ----

    suspend fun readText(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { reader -> reader.readText() }
        } catch (_: Exception) {
            null
        }
    }

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
     * Reads the document at [uri], unwrapping it first when it is a zip.
     * Zip-ness is decided on the leading bytes: neither the name nor the
     * provider's type survives a trip through every file manager and mail app.
     */
    suspend fun readTextOrZipped(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val stream = input.buffered()
                if (isZip(stream)) unzipText(stream) else stream.reader().readText()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Copies [source] into the document at [uri] as it arrives, so a
     * server-built export is never held in memory whole. Closing [source] is
     * the caller's job.
     */
    suspend fun writeStream(uri: Uri, source: InputStream): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val stream = context.contentResolver.openOutputStream(uri)
                    ?: return@withContext false
                stream.use { output -> source.copyTo(output) }
                true
            } catch (_: Exception) {
                false
            }
        }

    /**
     * Names an export after its subject, e.g. `acme-design-2026-07-28.json`; a
     * blank [subject] becomes `unknown`.
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

    /**
     * Names an export as the user wrote the subject: `Crm Testing.csv`. No
     * slug, kind or date - a spreadsheet is opened, not archived. Reserved
     * characters become spaces.
     */
    fun exportDisplayName(subject: String?, extension: String): String {
        val name = subject
            ?.replace(FILENAME_RESERVED, " ")
            ?.replace(WHITESPACE_RUN, " ")
            ?.trim()
            ?.takeIf { value -> value.isNotEmpty() }
            ?: "unknown"
        return "$name.$extension"
    }

    // ---- Names and types ----

    /**
     * A picked [uri]'s filename: the provider's display name, else the last
     * path segment, else [fallback]. An extension is added from the MIME type,
     * and `:` and `/` are stripped so the result is a valid cache filename.
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
     * Copies [uri] into the app cache under its real name. Use this rather than
     * a hand-rolled copy: a temp file named from the document id loses the
     * extension, and the upload's MIME type degrades to [DEFAULT_MIME].
     */
    suspend fun cacheFile(uri: Uri, fallbackName: String = "file"): File? =
        withContext(Dispatchers.IO) { copyToCache(uri, fallbackName) }

    /**
     * Copies each of [uris] into the cache in order; callers must [deleteAll]
     * them after the upload. Throws when one cannot be opened, so a picked
     * attachment is never silently dropped, and cleans up what was cached
     * first.
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
     * A multipart part from [file] under form field [field]. [mimeType]
     * overrides the type inferred from the extension.
     */
    fun filePart(field: String, file: File, mimeType: String? = null): MultipartBody.Part {
        val type = mimeType ?: mimeType(file)
        val body = file.asRequestBody(type.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(field, file.name, body)
    }

    /** [filePart] for each of [files], under the same form [field]. */
    fun fileParts(field: String, files: List<File>): List<MultipartBody.Part> =
        files.map { file -> filePart(field, file) }

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

    /** True when [stream] starts with a zip's local file header signature. */
    private fun isZip(stream: BufferedInputStream): Boolean {
        stream.mark(ZIP_MAGIC.size)
        val header = ByteArray(ZIP_MAGIC.size)
        val read = stream.read(header)
        stream.reset()
        return read == ZIP_MAGIC.size && header.contentEquals(ZIP_MAGIC)
    }

    /**
     * The text in a zipped export: the JSON entry, else the first real file.
     * Directories and `__MACOSX` are skipped.
     */
    private fun unzipText(stream: InputStream): String? {
        val zip = ZipInputStream(stream)
        var fallback: String? = null
        var entry = zip.nextEntry
        while (entry != null) {
            val name = entry.name
            if (!entry.isDirectory && !name.startsWith(MAC_METADATA_DIR)) {
                if (name.endsWith(".json", ignoreCase = true)) {
                    return zip.readBytes().decodeToString()
                }
                if (fallback == null) {
                    fallback = zip.readBytes().decodeToString()
                }
            }
            entry = zip.nextEntry
        }
        return fallback
    }

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

        // "PK" — the signature every zip opens with.
        private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

        // Resource-fork folder a Mac adds when it re-zips an archive.
        private const val MAC_METADATA_DIR = "__MACOSX/"

        // Characters no common file system takes in a name, and control codes.
        private val FILENAME_RESERVED = Regex("""[\\/:*?"<>|]|\p{Cntrl}""")

        private val WHITESPACE_RUN = Regex("""\s+""")

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
