// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.repository

import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mochios.android.api.unwrap
import org.mochios.android.model.Attachment
import org.mochios.feeds.api.FeedsApi
import org.mochios.feeds.model.Post
import org.mochios.feeds.model.SavedItem
import org.mochios.feeds.model.SavedSnapshot
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory mirror of the user's saved posts (server-side, per-user database)
 * so cards read [isSaved] synchronously; mutations apply optimistically.
 * Mirrors web lib/saved.ts. [load] once on entering feeds (the router does
 * this).
 */
@Singleton
class SavedRepository @Inject constructor(
    private val api: FeedsApi,
    private val gson: Gson,
) {
    private val _saved = MutableStateFlow<List<SavedItem>>(emptyList())
    val saved: StateFlow<List<SavedItem>> = _saved.asStateFlow()

    private val _savedIds = MutableStateFlow<Set<String>>(emptySet())
    val savedIds: StateFlow<Set<String>> = _savedIds.asStateFlow()

    fun isSaved(id: String): Boolean = _savedIds.value.contains(id)

    /** Fetch the saved list and populate the mirror. Errors are swallowed so the
     *  bookmark UI degrades gracefully (e.g. before login completes). */
    suspend fun load() {
        try {
            val saved = api.listSaved().unwrap().saved.map { item ->
                item.copy(post = item.post.withAttachmentDefaults())
            }
            _saved.value = saved
            _savedIds.value = saved.map { it.post.id }.toSet()
        } catch (_: Exception) {
            // Leave the existing cache untouched on failure.
        }
    }

    /** Toggle saved state. Returns the new state (true = now saved). Throws on
     *  API failure, after reverting the optimistic update. */
    suspend fun toggle(post: Post): Boolean =
        if (isSaved(post.id)) {
            remove(post.id); false
        } else {
            add(post); true
        }

    suspend fun add(post: Post) {
        if (isSaved(post.id)) return
        val snapshot = snapshotOf(post)
        val previousSaved = _saved.value
        val previousIds = _savedIds.value
        _saved.value = listOf(SavedItem(snapshot, System.currentTimeMillis() / 1000)) + previousSaved
        _savedIds.value = previousIds + post.id
        try {
            api.addSaved(post.id, gson.toJson(snapshot)).unwrap()
        } catch (e: Exception) {
            _saved.value = previousSaved
            _savedIds.value = previousIds
            throw e
        }
    }

    suspend fun remove(id: String) {
        val previousSaved = _saved.value
        val previousIds = _savedIds.value
        if (!previousIds.contains(id)) return
        _saved.value = previousSaved.filterNot { it.post.id == id }
        _savedIds.value = previousIds - id
        try {
            api.removeSaved(id).unwrap()
        } catch (e: Exception) {
            _saved.value = previousSaved
            _savedIds.value = previousIds
            throw e
        }
    }

    suspend fun clear() {
        val previousSaved = _saved.value
        val previousIds = _savedIds.value
        _saved.value = emptyList()
        _savedIds.value = emptySet()
        try {
            api.clearSaved().unwrap()
        } catch (e: Exception) {
            _saved.value = previousSaved
            _savedIds.value = previousIds
            throw e
        }
    }

}

/**
 * Snapshot persisted for a post, in the web schema. The server's
 * `body_markdown` is the rendered HTML (feeds.star sets it to
 * `markdown(body)`), so it goes in [SavedSnapshot.bodyHtml] and `body` stays
 * the raw source - web maps it the same way.
 */
internal fun snapshotOf(post: Post): SavedSnapshot = SavedSnapshot(
    id = post.id,
    feedId = post.feed,
    feedFingerprint = post.feedFingerprint,
    feedName = post.feedName,
    author = post.source?.name?.takeIf { it.isNotBlank() } ?: post.feedName,
    created = post.created,
    body = post.body,
    bodyHtml = post.bodyMarkdown,
    data = post.data,
    attachments = post.attachments,
    tags = post.tags,
)

/**
 * [this] with each attachment's text fields filled in. A snapshot is written
 * by whichever client saved the post, and one that leaves a field out - the
 * web's leaves out an empty caption - comes back from Gson as a null in a
 * non-null field: [Attachment] has no no-argument constructor for Gson to run
 * its defaults through. The casts make the null checks real ones.
 */
@Suppress("USELESS_CAST")
internal fun SavedSnapshot.withAttachmentDefaults(): SavedSnapshot = copy(
    attachments = (attachments as List<Attachment>?).orEmpty().map { attachment ->
        attachment.copy(
            name = (attachment.name as String?).orEmpty(),
            type = (attachment.type as String?).orEmpty(),
            caption = (attachment.caption as String?).orEmpty(),
        )
    },
)
