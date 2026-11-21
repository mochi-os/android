// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.util.REFRESH_DEBOUNCE
import org.mochios.android.util.mergeMessage
import java.util.UUID
import kotlin.time.Duration

/** One page of a game's chat as the server answers it, oldest or newest first. */
data class GameChatPage<T>(
    val messages: List<T>,
    val more: Boolean,
    val cursor: String?,
)

/**
 * What a game's chat shows. [messages] are oldest first; [cursor] reads the
 * page before the oldest of them. [error] is the failure of a first page that
 * left nothing to show: a refresh that fails keeps what is loaded.
 */
data class GameChatState<T>(
    val messages: List<T> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val cursor: String? = null,
    val error: MochiError? = null,
)

/**
 * The chat of one chess, go or words game: its newest page, the refresh that
 * follows every move, frame and return to the screen, older pages, frames, and
 * sending.
 *
 * A refresh folds the newest page into what is loaded, so the pages read
 * further back survive it and keep their cursor. Only a page sharing nothing
 * with the loaded rows replaces them: stitching the two would hide whatever was
 * said between.
 *
 * @param scope where the reads and sends run; a view model's.
 * @param fetch reads the page before a cursor, or the newest page for null.
 * @param post sends a message's body.
 * @param key a row's identity: its stored id, which a page's row and a
 *     frame's both carry, or for an older server's frame one from [frameRowId].
 * @param created a row's time, which orders the rows.
 * @param match a row's content, for a frame row from an older server only:
 *     that row stands in, under an id of this device's, until the row it
 *     matches arrives with its stored id.
 * @param report hears of an older page that failed to load.
 * @param debounce how long [refresh] waits for the next refresh to replace it.
 */
class GameChat<T>(
    private val scope: CoroutineScope,
    private val fetch: suspend (before: String?) -> GameChatPage<T>,
    private val post: suspend (body: String) -> Unit,
    private val key: (T) -> String,
    private val created: (T) -> Long,
    private val match: (T) -> String = key,
    private val report: (MochiError) -> Unit = {},
    private val debounce: Duration = REFRESH_DEBOUNCE,
) {
    private val _state = MutableStateFlow(GameChatState<T>())
    val state: StateFlow<GameChatState<T>> = _state.asStateFlow()

    /** The pending or running read of the newest page; a newer one cancels it. */
    private var reading: Job? = null

    /** Whether a page has set the paging, which frames alone never do. */
    private var paged = false

    /** Counts replacements, so an older page asked for before one is not stitched on after it. */
    private var generation = 0

    /** Read the newest page now, for the first load and a retry. */
    fun load() {
        reading?.cancel()
        reading = scope.launch { read() }
    }

    /** Read the newest page after [debounce], unless another refresh replaces this one first. */
    fun refresh() {
        reading?.cancel()
        reading = scope.launch {
            delay(debounce)
            read()
        }
    }

    private suspend fun read() {
        _state.update { it.copy(isLoading = true, error = null) }
        try {
            val page = fetch(null)
            _state.update { fold(it, page) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            val error = e.toMochiError()
            _state.update {
                it.copy(isLoading = false, error = if (it.messages.isEmpty()) error else null)
            }
        }
    }

    private fun fold(state: GameChatState<T>, page: GameChatPage<T>): GameChatState<T> {
        val newest = page.messages.sortedBy(created)
        val loaded = state.messages
        val disjoint = newest.isNotEmpty() && loaded.isNotEmpty() && run {
            val keys = loaded.mapTo(HashSet()) { row -> key(row) }
            newest.none { row -> key(row) in keys } && created(newest.first()) > created(loaded.last())
        }
        val first = !paged
        paged = true
        if (disjoint) generation += 1
        val folded = state.copy(
            messages = if (disjoint) newest else union(loaded, newest),
            isLoading = false,
            error = null,
        )
        // The paging stays as far back as it has been read, unless this page
        // is the first, or replaces what was read.
        return if (first || disjoint) folded.copy(hasMore = page.more, cursor = page.cursor) else folded
    }

    private fun union(loaded: List<T>, newest: List<T>): List<T> {
        if (newest.isEmpty()) return loaded
        if (loaded.isEmpty()) return newest
        // Each row of the page takes the place of one stand-in it matches.
        val standing = loaded.filter { row -> standsIn(key(row)) }.toMutableList()
        val replaced = HashSet<String>()
        for (row in newest) {
            val at = standing.indexOfFirst { stand -> match(stand) == match(row) }
            if (at >= 0) replaced += key(standing.removeAt(at))
        }
        val rows = LinkedHashMap<String, T>(loaded.size + newest.size)
        for (row in loaded) if (key(row) !in replaced) rows[key(row)] = row
        // The page's row wins: it carries edits the frame did not.
        for (row in newest) rows[key(row)] = row
        return rows.values.sortedBy(created)
    }

    /** Read the page before the oldest loaded row, once, while there is one. */
    fun loadMore() {
        val current = _state.value
        if (current.isLoadingMore || !current.hasMore) return
        val cursor = current.cursor ?: return
        val asked = generation
        scope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            try {
                val page = fetch(cursor)
                _state.update {
                    if (generation != asked) {
                        it.copy(isLoadingMore = false)
                    } else {
                        it.copy(
                            isLoadingMore = false,
                            messages = merge(it.messages, page.messages),
                            hasMore = page.more,
                            cursor = page.cursor,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingMore = false) }
                report(e.toMochiError())
            }
        }
    }

    private fun merge(loaded: List<T>, older: List<T>): List<T> {
        val seen = loaded.mapTo(HashSet()) { row -> key(row) }
        val added = older.filter { row -> seen.add(key(row)) }
        return if (added.isEmpty()) loaded else (added + loaded).sortedBy(created)
    }

    /**
     * A row a socket frame carried; one already loaded under its id is kept as
     * it is. An older server's frame carries no id: its row stands in only
     * while no stored row matches it, and gives way to the first that does.
     */
    fun receive(message: T) {
        _state.update { state ->
            val loaded = state.messages
            val messages = if (standsIn(key(message))) {
                if (loaded.any { row -> !standsIn(key(row)) && match(row) == match(message) }) loaded
                else mergeMessage(loaded, message, key, created)
            } else {
                val stand = loaded.firstOrNull { row -> standsIn(key(row)) && match(row) == match(message) }
                mergeMessage(if (stand == null) loaded else loaded - stand, message, key, created)
            }
            state.copy(messages = messages)
        }
    }

    /**
     * Send [body], then refresh so the row appears without waiting for its
     * frame. [onFinished] hears the failure, or null once it is sent.
     */
    fun send(body: String, onFinished: (MochiError?) -> Unit) {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) {
            onFinished(null)
            return
        }
        scope.launch {
            try {
                post(trimmed)
                refresh()
                onFinished(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFinished(e.toMochiError())
            }
        }
    }
}

/** The mark on an id this device made for a frame row, see [frameRowId]. */
private const val STANDIN = "frame:"

/**
 * The id of a row a socket frame carries: the stored row's, which servers
 * send, or for an older server's frame one unique on this device, marked as
 * standing in until the stored row arrives. Never built from the row's time
 * and sender: two messages from one player in one second would share it.
 */
fun frameRowId(id: Any?): String =
    (id as? String)?.takeIf { it.isNotBlank() } ?: STANDIN + UUID.randomUUID()

private fun standsIn(id: String): Boolean = id.startsWith(STANDIN)
