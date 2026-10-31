// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.repository

import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.unwrap
import org.mochios.android.auth.SessionManager
import org.mochios.forums.api.ForumsApi
import org.mochios.forums.model.Post
import org.mochios.forums.model.SavedItem
import org.mochios.forums.model.SavedSnapshot
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide store of saved (read-later) posts: a synchronous in-memory mirror so
 * post cards can read [isSaved] without awaiting; mutations apply
 * optimistically and reconcile with the server. Mirrors web's lib/saved.ts.
 * The posts are the signed-in account's own: signing out, or in as somebody
 * else, empties the mirror.
 */
@Singleton
class SavedRepository @Inject constructor(
    private val api: ForumsApi,
    private val gson: Gson,
    private val session: SessionManager,
) {
    private val _saved = MutableStateFlow<List<SavedItem>>(emptyList())
    val saved: StateFlow<List<SavedItem>> = _saved.asStateFlow()

    private val _savedIds = MutableStateFlow<Set<String>>(emptySet())
    val savedIds: StateFlow<Set<String>> = _savedIds.asStateFlow()

    /** Guards the mirror's bookkeeping against the session's watcher. */
    private val lock = Any()

    /** The account whose saved posts the mirror was read for; null while it
     *  holds none read. */
    private var account: String? = null

    /** Whether the watcher has seen the signed-in identity yet, and which. */
    private var watching = false
    private var seen: String? = null

    /** Moved on as the account changes: a read or a revert begun for the
     *  previous account leaves the mirror alone. */
    private var generation = 0

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            session.boundIdentity.collect { identity -> watch(identity) }
        }
    }

    /**
     * Empties the mirror whenever the signed-in identity changes, to or from
     * none included. The first identity seen only empties a mirror read for
     * someone else: a read already under way for it is kept.
     */
    private fun watch(identity: String?) {
        synchronized(lock) {
            if (watching && identity == seen) return
            val first = !watching
            watching = true
            seen = identity
            if (first && account == identity) return
            if (!first) generation++
            account = null
            _saved.value = emptyList()
            _savedIds.value = emptySet()
        }
    }

    fun isSaved(id: String): Boolean = _savedIds.value.contains(id)

    private fun started(): Int = synchronized(lock) { generation }

    /** Puts [saved] in the mirror, unless the account has changed since
     *  [started]; [identity] is the account it was read for. */
    private fun mirror(started: Int, saved: List<SavedItem>, ids: Set<String>, identity: String? = null) {
        synchronized(lock) {
            if (generation != started) return
            _saved.value = saved
            _savedIds.value = ids
            if (identity != null) account = identity
        }
    }

    suspend fun load() {
        // Read only for a known identity: read for none, a later sign-in
        // could not tell them from its own.
        val identity = session.getBoundIdentity() ?: return
        val started = started()
        try {
            val res = api.listSaved().unwrap()
            // Signed out, or in as somebody else, before the watcher has seen it.
            if (session.getBoundIdentity() != identity) return
            mirror(started, res.saved, res.saved.map { it.post.id }.toSet(), identity)
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
        val started = started()
        val previousSaved = _saved.value
        val previousIds = _savedIds.value
        _saved.value = listOf(SavedItem(snapshot, System.currentTimeMillis() / 1000)) + previousSaved
        _savedIds.value = previousIds + post.id
        try {
            api.addSaved(post.id, gson.toJson(snapshot)).unwrap()
        } catch (e: Exception) {
            mirror(started, previousSaved, previousIds)
            throw e
        }
    }

    suspend fun remove(id: String) {
        val previousSaved = _saved.value
        val previousIds = _savedIds.value
        if (!previousIds.contains(id)) return
        val started = started()
        _saved.value = previousSaved.filterNot { it.post.id == id }
        _savedIds.value = previousIds - id
        try {
            api.removeSaved(id).unwrap()
        } catch (e: Exception) {
            mirror(started, previousSaved, previousIds)
            throw e
        }
    }

    suspend fun clear() {
        val started = started()
        val previousSaved = _saved.value
        val previousIds = _savedIds.value
        _saved.value = emptyList()
        _savedIds.value = emptySet()
        try {
            api.clearSaved().unwrap()
        } catch (e: Exception) {
            mirror(started, previousSaved, previousIds)
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
