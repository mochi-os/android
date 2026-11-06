// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.components

import org.mochios.android.ui.components.addressByAuthor
import org.mochios.android.util.segment
import org.mochios.forums.model.ForumComment
import org.mochios.forums.model.Post

/**
 * Where a post author's avatar is served: under the post. Null when an id, a
 * peer's, cannot be one segment of the address ([segment]).
 */
internal fun postAvatar(forumId: String, postId: String): String? {
    val forum = segment(forumId) ?: return null
    val post = segment(postId) ?: return null
    return "/forums/$forum/-/$post/asset/avatar"
}

/**
 * Where a commenter's avatar is served: under one of their comments. Null when
 * an id, a peer's, cannot be one segment of the address ([segment]).
 */
internal fun commentAvatar(forumId: String, postId: String, commentId: String): String? {
    val forum = segment(forumId) ?: return null
    val post = segment(postId) ?: return null
    val comment = segment(commentId) ?: return null
    return "/forums/$forum/-/$post/$comment/asset/avatar"
}

/**
 * Whether a row may stand in for its author. The route answers for a pending
 * or removed row only to its author and the forum's managers, so in a list
 * other readers see only an approved row qualifies.
 */
internal fun approved(status: String) = status.isEmpty() || status == "approved"

/**
 * One avatar address per author across [posts] and then [comments], each from
 * the author's first row that may [standIn]. The route names a post or comment
 * rather than the person, so building each row's own address had the image
 * cache fetch and store the same picture once per row. [postForum] names the
 * forum a post's address goes under; comments go under [commentForum].
 */
internal fun authorAvatars(
    posts: List<Post>,
    postForum: (Post) -> String,
    comments: List<ForumComment> = emptyList(),
    commentForum: String = "",
    standIn: (status: String) -> Boolean = ::approved,
): Map<String, String> {
    val found = mutableMapOf<String, String>()
    addressByAuthor(
        posts.filter { it.id.isNotEmpty() },
        { it.member },
        { if (standIn(it.status)) postAvatar(postForum(it), it.id) else null },
        found = found,
    )
    return addressByAuthor(
        comments,
        { it.member },
        { if (standIn(it.status)) commentAvatar(commentForum, it.post, it.id) else null },
        { it.children },
        found,
    )
}
