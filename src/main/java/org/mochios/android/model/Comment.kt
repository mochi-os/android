package org.mochios.android.model

import com.google.gson.annotations.SerializedName

data class Comment(
    val id: String,
    val parent: String = "",
    val author: String = "",
    val name: String = "",
    val body: String = "",
    val content: String = "",
    @SerializedName("body_markdown") val bodyMarkdown: String = "",
    val created: Long = 0,
    @SerializedName("created_string") val createdString: String = "",
    val edited: Long = 0,
    val children: List<Comment> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    @SerializedName("my_reaction") val myReaction: String = "",
    val reactions: List<Reaction> = emptyList()
) {
    /** Returns body text — projects uses 'content', feeds uses 'body' */
    val text: String get() = content.ifBlank { body }
}
