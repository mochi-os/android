// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.repository

import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mochios.android.api.unwrap
import org.mochios.feeds.api.FeedsApi
import org.mochios.feeds.model.Post
import org.mochios.feeds.model.SavedItem
import org.mochios.feeds.model.SavedSnapshot
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide store for the user's saved ("read-later") posts. Saved posts live in
 * the feeds app's own per-user database on the user's node (so they persist and
 * sync across devices); this singleton keeps a synchronous in-memory mirror so
 * every post card can read [isSaved] without awaiting, while mutations apply
 * optimistically and reconcile with the server in the background.
 *
 * Mirrors the web client's lib/saved.ts. Call [load] once after entering the
 * feeds area (done by the router) to hydrate.
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

    // Build the slim snapshot persisted for a post, matching the web schema so a
    // post saved on Android renders on web and vice versa. Android's `body` is
    // the rendered HTML and `bodyMarkdown` the raw source (the reverse of the
    // snapshot's field names). Feeds posts carry no separate author, so fall
    // back to the external source name or the feed's own name.
    private fun snapshotOf(post: Post): SavedSnapshot = SavedSnapshot(
        id = post.id,
        feedId = post.feed,
        feedFingerprint = post.feedFingerprint,
        feedName = post.feedName,
        author = post.source?.name?.takeIf { it.isNotBlank() } ?: post.feedName,
        created = post.created,
        body = post.bodyMarkdown,
        bodyHtml = post.body,
        attachments = post.attachments,
    )
}
