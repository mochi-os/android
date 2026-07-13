// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.model

data class Attachment(
    val id: String = "",
    val name: String = "",
    val size: Long = 0,
    val type: String = "",
    val created: Long = 0,
)

data class AttachmentsResponse(
    val attachments: List<Attachment> = emptyList(),
)

data class AttachmentUploadResponse(
    val attachments: List<Attachment> = emptyList(),
)

data class AttachmentDeleteResponse(
    val ok: Boolean = false,
)
