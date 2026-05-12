package org.mochios.forums.model

import com.google.gson.annotations.SerializedName

data class Forum(
    val id: String = "",
    val fingerprint: String = "",
    val name: String = "",
    val updated: Long = 0,
    @SerializedName("can_manage") val canManage: Boolean = false,
    @SerializedName("can_post") val canPost: Boolean = false,
    @SerializedName("can_moderate") val canModerate: Boolean = false,
    val server: String = "",
    val banner: String = "",
    @SerializedName("banner_html") val bannerHtml: String = "",
    val sort: String = ""
)

data class Member(
    val forum: String = "",
    val id: String = "",
    val name: String = "",
    val subscribed: Long = 0
)

data class Tag(
    val id: String = "",
    val label: String = "",
    val qid: String = "",
    val source: String = "",
    val relevance: Float = 0f,
    val interest: Float = 0f
)

// Forums shares the lib/android `Attachment` shape — see
// org.mochios.android.model.Attachment. PostAttachment was an older,
// duplicated form; the type alias keeps existing references compiling.
typealias PostAttachment = org.mochios.android.model.Attachment

data class Post(
    val id: String = "",
    val forum: String = "",
    val fingerprint: String = "",
    val member: String = "",
    val name: String = "",
    val title: String = "",
    val body: String = "",
    @SerializedName("body_markdown") val bodyMarkdown: String = "",
    val comments: Int = 0,
    val up: Int = 0,
    val down: Int = 0,
    val created: Long = 0,
    val updated: Long = 0,
    val edited: Long = 0,
    @SerializedName("user_vote") val userVote: String = "",
    val attachments: List<PostAttachment> = emptyList(),
    val forumName: String = "",
    val tags: List<Tag> = emptyList(),
    val status: String = "",
    val locked: Boolean = false,
    val pinned: Boolean = false,
    val remover: String = "",
    val reason: String = ""
)

data class ForumComment(
    val id: String = "",
    val forum: String = "",
    val post: String = "",
    val parent: String = "",
    val member: String = "",
    val name: String = "",
    val body: String = "",
    val up: Int = 0,
    val down: Int = 0,
    val created: Long = 0,
    val edited: Long = 0,
    @SerializedName("user_vote") val userVote: String = "",
    val children: List<ForumComment> = emptyList(),
    val attachments: List<org.mochios.android.model.Attachment> = emptyList(),
    @SerializedName("can_vote") val canVote: Boolean = false,
    @SerializedName("can_comment") val canComment: Boolean = false,
    val status: String = "",
    val remover: String = "",
    val reason: String = ""
)

data class DirectoryEntry(
    val id: String = "",
    val fingerprint: String = "",
    @SerializedName("fingerprint_hyphens") val fingerprintHyphens: String = "",
    val name: String = "",
    @SerializedName("class") val klass: String = "",
    val data: String = "",
    val location: String = "",
    val created: Long = 0,
    val updated: Long = 0,
    val subscribed: Boolean = false
)

data class RecommendedForum(
    val id: String = "",
    val name: String = "",
    val blurb: String = "",
    val fingerprint: String = "",
    val server: String = ""
)
