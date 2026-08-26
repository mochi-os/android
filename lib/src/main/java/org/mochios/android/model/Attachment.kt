// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.model

import com.google.gson.annotations.SerializedName

data class Attachment(
    val id: String,
    val name: String = "",
    val size: Long = 0,
    val type: String = "",
    val created: Long = 0,
    val caption: String = "",
    val url: String? = null,
    @SerializedName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerializedName("preview_url") val previewUrl: String? = null
) {
    val fileKind: FileKind get() = fileKindOf(type, name)

    val isImage: Boolean get() = fileKind == FileKind.IMAGE
    val isVideo: Boolean get() = fileKind == FileKind.VIDEO
    val isAudio: Boolean get() = fileKind == FileKind.AUDIO
}

/** File category for an [Attachment], used to route media and choose icons. */
enum class FileKind { IMAGE, VIDEO, AUDIO, PDF, WORD, EXCEL, TEXT, OTHER }

/**
 * The kind a file of this MIME type and name belongs to. Takes the two fields
 * rather than an [Attachment] so the apps that carry an attachment shape of
 * their own - the wikis one, for instance - sort their files by the same rules
 * and so end up with the same icon and colour as everywhere else.
 */
fun fileKindOf(type: String, name: String): FileKind {
    val ext = name.substringAfterLast('.', "").lowercase()
    // The kind can arrive as a full MIME type ("image/png") or a bare
    // kind ("image"); match on the part before any slash, with the
    // filename extension as a fallback.
    val typeKind = type.substringBefore('/').lowercase()
    return when {
        typeKind == "image" -> FileKind.IMAGE
        typeKind == "video" -> FileKind.VIDEO
        typeKind == "audio" || ext in AUDIO_EXTENSIONS -> FileKind.AUDIO
        ext == "pdf" || type == "application/pdf" -> FileKind.PDF
        ext == "doc" || ext == "docx" || type in WORD_MIME_TYPES -> FileKind.WORD
        ext == "xls" || ext == "xlsx" || type in EXCEL_MIME_TYPES -> FileKind.EXCEL
        ext == "txt" || type == "text/plain" -> FileKind.TEXT
        else -> FileKind.OTHER
    }
}

private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "m4a", "aac", "ogg", "oga", "flac", "opus")

private val WORD_MIME_TYPES = setOf(
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
)

private val EXCEL_MIME_TYPES = setOf(
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
)
