package org.mochios.wikis.model

import com.google.gson.annotations.SerializedName

data class Revision(
    val id: String = "",
    val title: String = "",
    val author: String = "",
    val name: String = "",
    val created: Long = 0,
    val version: Int = 0,
    val comment: String = "",
)

data class RevisionDetail(
    val id: String = "",
    val title: String = "",
    val author: String = "",
    val name: String = "",
    val created: Long = 0,
    val version: Int = 0,
    val comment: String = "",
    val content: String = "",
)

data class PageHistoryResponse(
    val page: String = "",
    val revisions: List<Revision> = emptyList(),
)

data class PageRevisionResponse(
    val page: String = "",
    val revision: RevisionDetail = RevisionDetail(),
    @SerializedName("current_version")
    val currentVersion: Int = 0,
)
