// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.repository

import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mochios.android.api.unwrap
import org.mochios.forums.api.ForumsApi
import org.mochios.forums.model.Post
import org.mochios.forums.model.SavedItem
import org.mochios.forums.model.SavedSnapshot
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide store for the user's saved ("read-later") forum posts. Saved posts
 * live in the forums app's own per-user database (so they persist and sync
 * across devices); this singleton keeps a synchronous in-memory mirror so every
 * post card can read [isSaved] without awaiting, while mutations apply
 * optimistically and reconcile with the server in the background. Mirrors the
 * web client's lib/saved.ts.
 */
@Singleton
class SavedRepository @Inject constructor(
    private val api: ForumsApi,
    private val gson: Gson,
) {
    private val _saved = MutableStateFlow<List<SavedItem>>(emptyList())
    val saved: StateFlow<List<SavedItem>> = _saved.asStateFlow()

    private val _savedIds = MutableStateFlow<Set<String>>(emptySet())
    val savedIds: StateFlow<Set<String>> = _savedIds.asStateFlow()

    fun isSaved(id: String): Boolean = _savedIds.value.contains(id)

    suspend fun load() {
        try {
            val res = api.listSaved().unwrap()
            _saved.value = res.saved
            _savedIds.value = res.saved.map { it.post.id }.toSet()
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

    private fun snapshotOf(post: Post): SavedSnapshot = SavedSnapshot(
        id = post.id,
        forum = post.forum,
        fingerprint = post.fingerprint,
        forumName = post.forumName,
        member = post.member,
        name = post.name,
        title = post.title,
        body = post.body,
        bodyMarkdown = post.bodyMarkdown,
        created = post.created,
        up = post.up,
        down = post.down,
        tags = post.tags,
        attachments = post.attachments,
    )
}
