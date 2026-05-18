package org.mochios.wikis.model

data class Tag(
    val tag: String = "",
    val count: Int = 0,
)

data class TagsResponse(
    val tags: List<Tag> = emptyList(),
)

data class TagPage(
    val page: String = "",
    val title: String = "",
    val updated: Long = 0,
)

data class TagPagesResponse(
    val tag: String = "",
    val pages: List<TagPage> = emptyList(),
)

data class TagAddResponse(
    val ok: Boolean = false,
    val added: Boolean = false,
)

data class TagRemoveResponse(
    val ok: Boolean = false,
)
