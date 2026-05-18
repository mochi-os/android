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
