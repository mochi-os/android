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
    val url: String? = null,
    @SerializedName("thumbnail_url") val thumbnailUrl: String? = null
) {
    /**
     * Coarse file category, from the MIME [type] first and the filename
     * extension as a fallback. Drives both media routing (image/video viewers)
     * and the icon shown for document attachments.
     */
    val fileKind: FileKind
        get() {
            val ext = name.substringAfterLast('.', "").lowercase()
            // The kind can arrive as a MIME type ("image/png"), a bare kind
            // ("image"), or a Mochi entity-reference name ("image:59") that has
            // neither a slash nor a file extension — accept all three.
            val typeKind = type.substringBefore('/').lowercase()
            val nameKind = if (name.contains(':')) name.substringBefore(':').lowercase() else ""
            return when {
                typeKind == "image" || nameKind == "image" -> FileKind.IMAGE
                typeKind == "video" || nameKind == "video" -> FileKind.VIDEO
                typeKind == "audio" || nameKind == "audio" || ext in AUDIO_EXTENSIONS -> FileKind.AUDIO
                ext == "pdf" || type == "application/pdf" || nameKind == "pdf" -> FileKind.PDF
                ext == "doc" || ext == "docx" || type in WORD_MIME_TYPES ||
                    nameKind in WORD_KINDS -> FileKind.WORD
                ext == "xls" || ext == "xlsx" || type in EXCEL_MIME_TYPES ||
                    nameKind in EXCEL_KINDS -> FileKind.EXCEL
                ext == "txt" || type == "text/plain" || nameKind == "txt" || nameKind == "text" ->
                    FileKind.TEXT
                else -> FileKind.OTHER
            }
        }

    val isImage: Boolean get() = fileKind == FileKind.IMAGE
    val isVideo: Boolean get() = fileKind == FileKind.VIDEO
    val isAudio: Boolean get() = fileKind == FileKind.AUDIO

    private companion object {

        val AUDIO_EXTENSIONS = setOf("mp3", "wav", "m4a", "aac", "ogg", "oga", "flac", "opus")

        val WORD_MIME_TYPES = setOf(
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        )

        val EXCEL_MIME_TYPES = setOf(
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        )

        // Entity-reference name prefixes ("word:12") the server may use in place
        // of a filename or MIME type.
        val WORD_KINDS = setOf("doc", "docx", "word")

        val EXCEL_KINDS = setOf("xls", "xlsx", "excel", "spreadsheet")
    }
}

/** File category for an [Attachment], used to route media and choose icons. */
enum class FileKind { IMAGE, VIDEO, AUDIO, PDF, WORD, EXCEL, TEXT, OTHER }
