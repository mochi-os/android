// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Single source of truth for building file-upload multipart parts across the
 * app. Every upload should go through here so each part carries the real
 * filename and MIME type: the server keeps both, and dropping them (e.g. a
 * blanket `application/octet-stream`, or a content-uri document id used as the
 * name) is what leaves an attachment unrecognisable and un-previewable later.
 */
object Uploads {

    /** Fallback content type when a real MIME type can't be determined. */
    const val DEFAULT_MIME = "application/octet-stream"

    /** MIME type for [file], from its extension; [DEFAULT_MIME] when unknown. */
    fun mimeType(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: DEFAULT_MIME

    /** MIME type [resolver] reports for [uri]; [DEFAULT_MIME] when unknown. */
    fun mimeType(resolver: ContentResolver, uri: Uri): String =
        resolver.getType(uri) ?: DEFAULT_MIME

    /**
     * A multipart part built from [file] under form field [field], carrying the
     * file's name and MIME type. [mimeType] defaults to the type inferred from
     * the file's extension; pass an explicit value when the server requires a
     * specific type regardless of the file (e.g. a re-encoded JPEG upload).
     */
    fun filePart(
        field: String,
        file: File,
        mimeType: String = mimeType(file)
    ): MultipartBody.Part {
        val body = file.asRequestBody(mimeType.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(field, file.name, body)
    }

    /** [filePart] for each of [files], under the same form [field]. */
    fun fileParts(field: String, files: List<File>): List<MultipartBody.Part> =
        files.map { file -> filePart(field, file) }

    /**
     * A multipart part from in-memory [bytes] under form field [field], carrying
     * [fileName] and [mimeType]. For callers that already hold the bytes and
     * type (no [File] or content [Uri] to resolve from).
     */
    fun bytesPart(
        field: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): MultipartBody.Part =
        MultipartBody.Part.createFormData(field, fileName, bytes.toRequestBody(mimeType.toMediaTypeOrNull()))

    /**
     * A multipart part built by reading [uri]'s bytes through [resolver], under
     * form field [field], carrying the resolved display name and MIME type.
     *
     * @throws IOException when the uri cannot be opened.
     */
    fun uriPart(
        resolver: ContentResolver,
        field: String,
        uri: Uri,
        fallbackName: String = "file"
    ): MultipartBody.Part {
        val bytes = resolver.openInputStream(uri)?.use { stream -> stream.readBytes() }
            ?: throw IOException("Cannot read $uri")
        val body = bytes.toRequestBody(mimeType(resolver, uri).toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(field, fileName(resolver, uri, fallbackName), body)
    }

    /**
     * Resolves a picked content [uri] to a real filename with an extension:
     * prefers the provider's display name, falls back to the last path segment
     * then [fallback], and appends an extension from the MIME type when missing.
     * `:` and `/` are stripped so the result is a valid cache filename.
     */
    fun fileName(resolver: ContentResolver, uri: Uri, fallback: String): String {
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
            val ext = MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(resolver.getType(uri).orEmpty())
            if (!ext.isNullOrBlank()) {
                name = "$name.$ext"
            }
        }
        return name.ifBlank { fallback }
    }
}
