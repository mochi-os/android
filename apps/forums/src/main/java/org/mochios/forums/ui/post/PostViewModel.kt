// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.post

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.Upload
import org.mochios.android.files.UploadTracker
import org.mochios.android.util.REFRESH_DEBOUNCE
import org.mochios.android.util.UndoChain
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.forums.R
import org.mochios.forums.model.Forum
import org.mochios.forums.model.ForumComment
import org.mochios.forums.model.Post
import org.mochios.forums.model.rejectMessage
import org.mochios.forums.repository.ForumsRepository
import javax.inject.Inject

data class PostUiState(
    val forum: Forum = Forum(),
    val post: Post = Post(),
    val comments: List<ForumComment> = emptyList(),
    val canVote: Boolean = false,
    val canComment: Boolean = false,
    /** Whether the reader manages the forum, which the server requires to
     *  edit or delete someone else's post or comment. */
    val canManage: Boolean = false,
    val canModerate: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isSending: Boolean = false,
    val error: MochiError? = null,
    val replyTo: ForumComment? = null,
    val deleted: Boolean = false,
    /** Bound identity for the current session — used to gate the Edit
     *  menu items on (author == me) || canManage. */
    val identity: String = "",
    /** Message resource for a rejection the forum owner has just pushed. */
    @StringRes val rejected: Int? = null,
    /** Shown in place of the thread once the forum's owner removed this user
     *  from it, or deleted it, while the screen was open. */
    @StringRes val gone: Int? = null,
    /** Whether the user follows the post; null where they cannot. */
    val following: Boolean? = null,
    /** What an action that worked says, as a string resource. */
    @StringRes val done: Int? = null,
)

@HiltViewModel
class PostViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val application: android.app.Application,
    private val repository: ForumsRepository,
    private val sessionManager: SessionManager,
    private val webSocket: MochiWebSocket,
) : ViewModel() {

    /** The picked file's real name, for labelling a draft attachment. */
    suspend fun fileName(uri: Uri): String = repository.fileName(uri)

    val forumId: String = savedStateHandle["forumId"] ?: ""
    val postId: String = savedStateHandle["postId"] ?: ""
    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

    /**
     * The comment the post is opened on, shown alone with its replies, as a
     * shared link to it opens the post; null for the whole thread.
     */
    val focusedComment: StateFlow<String?> = savedStateHandle.getStateFlow(COMMENT, null)

    /** Open the post on [comment] and its replies, or on the whole thread for null. */
    fun openComment(comment: String?) {
        savedStateHandle[COMMENT] = comment
    }

    /**
     * The web address of one comment, which opens this post on it, as the web
     * client's Share builds it.
     */
    fun commentLink(comment: String): String {
        val forum = _uiState.value.forum.fingerprint.ifEmpty { forumId }
        return "$serverUrl/forums/$forum/$postId?comment=${Uri.encode(comment)}"
    }

    private val _uiState = MutableStateFlow(PostUiState())
    val uiState: StateFlow<PostUiState> = _uiState.asStateFlow()

    private val commentTracker = UploadTracker()

    /** The files of the comment being sent, for the composer. */
    val commentUpload: StateFlow<Upload?> = commentTracker.state

    private var subscriptionId: String? = null

    /** The pending or in-flight [refresh]; cancelled when a newer one starts. */
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            val id = sessionManager.getBoundIdentity().orEmpty()
            _uiState.value = _uiState.value.copy(identity = id)
        }
        load()
    }

    private fun subscribeWebSocket(forumKey: String) {
        if (forumKey.isBlank() || subscriptionId != null) return
        subscriptionId = webSocket.subscribe(
            sessionManager.getServerUrlBlocking(),
            forumKey,
            app = "forums",
        ) { event ->
            // The forum was left, deleted or renamed: the held roster no longer
            // says so, and the drawer reads it next.
            if (event.type.orEmpty().startsWith("forum/")) repository.forget()
            // The owner removed this user from the forum, or deleted it. The
            // local replica is already purged, so a reload would only fail.
            if (event.type == "forum/removed" || event.type == "forum/deleted") {
                _uiState.value = _uiState.value.copy(
                    gone = if (event.type == "forum/removed") {
                        R.string.forums_removed_from_forum
                    } else {
                        R.string.forums_forum_deleted
                    },
                )
                return@subscribe
            }
            // A rejection deletes the author's optimistic copy, so a silent
            // reload alone would make their comment vanish with no reason.
            if (event.type == "post/reject" || event.type == "comment/reject") {
                _uiState.value = _uiState.value.copy(
                    rejected = rejectMessage(event.reason, event.type == "comment/reject"),
                )
            }
            if (!_uiState.value.deleted) {
                reloadQuietly()
            }
        }
    }

    /**
     * Refetch the post for a forum frame, with no spinner. It shares [refresh]'s
     * job and debounce, so a burst of frames, or a frame landing with an
     * action's own refresh, makes one fetch.
     */
    private fun reloadQuietly() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(REFRESH_DEBOUNCE)
            try {
                val r = repository.viewPost(forumId, postId)
                _uiState.value = _uiState.value.copy(
                    forum = r.forum,
                    post = r.post,
                    comments = r.comments,
                    isRefreshing = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        subscriptionId?.let { webSocket.unsubscribe(it) }
    }

    /** The rejection snackbar has been shown; don't repeat it. */
    fun clearRejected() {
        _uiState.value = _uiState.value.copy(rejected = null)
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val r = repository.viewPost(forumId, postId)
                _uiState.value = _uiState.value.copy(
                    forum = r.forum,
                    post = r.post,
                    comments = r.comments,
                    canVote = r.can_vote,
                    canComment = r.can_comment,
                    canManage = r.can_manage,
                    canModerate = r.can_moderate,
                    following = r.following,
                    isLoading = false
                )
                subscribeWebSocket(r.forum.fingerprint)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    /**
     * Refetch the post and its comments. Each call cancels the previous
     * refresh and waits [REFRESH_DEBOUNCE] first, so a burst of forum frames -
     * our own action's echo lands with the action's response - makes one
     * fetch, and only the latest one updates the post.
     */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(REFRESH_DEBOUNCE)
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val r = repository.viewPost(forumId, postId)
                _uiState.value = _uiState.value.copy(
                    forum = r.forum,
                    post = r.post,
                    comments = r.comments,
                    canVote = r.can_vote,
                    canComment = r.can_comment,
                    canManage = r.can_manage,
                    canModerate = r.can_moderate,
                    following = r.following,
                    isRefreshing = false,
                    error = null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isRefreshing = false, error = e.toMochiError())
            }
        }
    }

    // Votes, lock and pin show on the tap and go back if the server refuses,
    // as on the web. Each goes back on its own item, and only while it still
    // shows the tap's guess, so another vote, a lock or a reload that changed
    // the post meanwhile stays.
    private val votes = UndoChain<Tally>()
    private val locks = UndoChain<Boolean>()
    private val pins = UndoChain<Boolean>()

    /** Taps still waiting on the server. */
    private var waiting = 0

    /** Whether a tap stopped a reload, which goes out once no tap waits. */
    private var stopped = false

    /**
     * Show [guess] on what [read] reads at once, send [request], and if the
     * server refuses put back what showed before with the error. [read] gives
     * null when the item is not on screen. The post reloads once the server
     * has the change.
     */
    private fun <F> instant(
        chain: UndoChain<F>,
        id: String,
        read: (PostUiState) -> F?,
        write: (PostUiState, F) -> PostUiState,
        guess: (F) -> F,
        request: suspend () -> Unit,
    ) {
        val now = read(_uiState.value)
        val shown = now?.let(guess)
        val step = if (now != null && shown != now) chain.start(id) else null
        if (step != null && now != null && shown != null) {
            step.remember(now)
            _uiState.value = write(_uiState.value, shown)
        }
        // A reload on its way would land after the guess and put the old
        // value back, so it is stopped until no tap waits.
        waiting += 1
        if (refreshJob?.isActive == true) {
            refreshJob?.cancel()
            stopped = true
        }
        viewModelScope.launch {
            try {
                request()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                waiting -= 1
                var state = _uiState.value
                val before = step?.refused()
                if (before != null && read(state) == shown) state = write(state, before.value)
                _uiState.value = state.copy(error = e.toMochiError())
                if (waiting == 0 && stopped) {
                    stopped = false
                    reloadQuietly()
                }
                return@launch
            }
            waiting -= 1
            step?.accepted()
            stopped = false
            refresh()
        }
    }

    fun votePost(vote: String) = instant(
        votes,
        postId,
        read = { it.post.tally() },
        write = { state, tally -> state.copy(post = state.post.with(tally)) },
        guess = { it.after(vote) },
    ) { repository.votePost(forumId, postId, vote) }

    fun voteComment(commentId: String, vote: String) = instant(
        votes,
        commentId,
        read = { findComment(it.comments, commentId)?.tally() },
        write = { state, tally ->
            state.copy(comments = changeComment(state.comments, commentId) { it.with(tally) })
        },
        guess = { it.after(vote) },
    ) { repository.voteComment(forumId, postId, commentId, vote) }

    fun setReplyTo(comment: ForumComment?) {
        _uiState.value = _uiState.value.copy(replyTo = comment)
    }

    /** Files picked for the pending comment, in upload order. */
    private val _commentAttachments = MutableStateFlow<List<android.net.Uri>>(emptyList())
    val commentAttachments: StateFlow<List<android.net.Uri>> = _commentAttachments.asStateFlow()

    fun addCommentAttachments(uris: List<android.net.Uri>) {
        _commentAttachments.value = _commentAttachments.value +
            uris.filterNot { uri -> uri in _commentAttachments.value }
    }

    fun removeCommentAttachment(uri: android.net.Uri) {
        _commentAttachments.value = _commentAttachments.value.filterNot { it == uri }
    }

    /**
     * Sends the draft. From the lightbox's comments panel, [anchor] is the
     * image the comment is about; a reply keeps its parent's context instead.
     */
    fun submitComment(body: String, anchor: String? = null) {
        val trimmed = body.trim()
        // The server rejects an empty body with 400, even when files are attached.
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            try {
                val parent = _uiState.value.replyTo?.id
                val uris = _commentAttachments.value
                commentTracker.track(uris) {
                    repository.createCommentFromUris(
                        forumId = forumId,
                        postId = postId,
                        body = trimmed,
                        parent = parent,
                        uris = uris,
                        context = application,
                        attachment = anchor,
                        tracker = commentTracker,
                    )
                    _commentAttachments.value = emptyList()
                    _uiState.value = _uiState.value.copy(replyTo = null)
                    refresh()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            } finally {
                _uiState.value = _uiState.value.copy(isSending = false)
            }
        }
    }

    fun deletePost() {
        viewModelScope.launch {
            try {
                repository.deletePost(forumId, postId)
                refreshJob?.cancel()
                subscriptionId?.let { id -> webSocket.unsubscribe(id) }
                subscriptionId = null
                _uiState.value = _uiState.value.copy(deleted = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            try {
                repository.deleteComment(forumId, postId, commentId)
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    /**
     * Edit a comment's body. Body only, as on web: `action_comment_edit`
     * applies attachment changes on the owner branch alone, and forwards
     * `{id, body}` on the subscriber branch — sending files there confirms an
     * edit the server discards.
     */
    fun editComment(commentId: String, newBody: String) {
        viewModelScope.launch {
            try {
                repository.editComment(forumId, postId, commentId, newBody)
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    /** Follow flips on the tap and back, with the error, if the server
     *  refuses; the post reloads once the server has it. */
    fun setFollowing(following: Boolean) {
        val before = _uiState.value.following
        _uiState.value = _uiState.value.copy(following = following)
        viewModelScope.launch {
            try {
                repository.followPost(forumId, postId, following)
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A later tap has flipped it since, and its own answer settles it.
                val state = _uiState.value
                _uiState.value = state.copy(
                    following = if (state.following == following) before else state.following,
                    error = e.toMochiError(),
                )
            }
        }
    }

    fun pinPost() = pin(true) { repository.pinPost(forumId, postId) }
    fun unpinPost() = pin(false) { repository.unpinPost(forumId, postId) }
    fun lockPost() = lock(true) { repository.lockPost(forumId, postId) }
    fun unlockPost() = lock(false) { repository.unlockPost(forumId, postId) }

    private fun pin(pinned: Boolean, request: suspend () -> Unit) = instant(
        pins,
        postId,
        read = { it.post.pinned },
        write = { state, value -> state.copy(post = state.post.copy(pinned = value)) },
        guess = { pinned },
        request = request,
    )

    private fun lock(locked: Boolean, request: suspend () -> Unit) = instant(
        locks,
        postId,
        read = { it.post.locked },
        write = { state, value -> state.copy(post = state.post.copy(locked = value)) },
        guess = { locked },
        request = request,
    )
    fun approvePost() = moderate { repository.approvePost(forumId, postId) }
    fun removePost() = moderate { repository.removePost(forumId, postId) }
    fun restorePost() = moderate { repository.restorePost(forumId, postId) }
    fun reportPost(reason: String, details: String) =
        moderate { repository.reportPost(forumId, postId, reason, details) }

    fun removeComment(commentId: String) =
        moderate { repository.removeComment(forumId, postId, commentId) }
    fun restoreComment(commentId: String) =
        moderate { repository.restoreComment(forumId, postId, commentId) }
    fun approveComment(commentId: String) =
        moderate { repository.approveComment(forumId, postId, commentId) }
    fun reportComment(commentId: String, reason: String, details: String) =
        moderate { repository.reportComment(forumId, postId, commentId, reason, details) }

    fun addPostTag(label: String) = moderate {
        repository.addPostTag(forumId, postId, label)
    }

    fun removePostTag(tagId: String) = moderate {
        repository.removePostTag(forumId, postId, tagId)
    }

    fun adjustTagInterest(qid: String, direction: String) {
        viewModelScope.launch {
            try {
                repository.adjustTagInterest(forumId, qid, direction)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    /**
     * Block a person on another network by their address, or their server by
     * its domain: their posts and comments are hidden everywhere after.
     */
    fun block(target: String) {
        if (target.isBlank()) return
        viewModelScope.launch {
            try {
                repository.addBlock(target)
                _uiState.value = _uiState.value.copy(done = R.string.forums_blocked)
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun clearDone() {
        _uiState.value = _uiState.value.copy(done = null)
    }

    private fun moderate(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }
}

/** The post route's argument naming the comment it is opened on. */
private const val COMMENT = "comment"

/** The counts a vote changes, and the reader's own vote: "up", "down" or "". */
private data class Tally(val up: Int, val down: Int, val vote: String) {
    /** After the reader's vote changes to [vote], as the web client sums it on the tap. */
    fun after(vote: String): Tally {
        if (vote == this.vote) return this
        var up = up
        var down = down
        if (this.vote == "up") up -= 1
        if (this.vote == "down") down -= 1
        if (vote == "up") up += 1
        if (vote == "down") down += 1
        return Tally(up, down, vote)
    }
}

private fun Post.tally() = Tally(up, down, userVote)

private fun Post.with(tally: Tally) = copy(up = tally.up, down = tally.down, userVote = tally.vote)

private fun ForumComment.tally() = Tally(up, down, userVote)

private fun ForumComment.with(tally: Tally) =
    copy(up = tally.up, down = tally.down, userVote = tally.vote)

/** The comment with [id] anywhere in the thread. */
private fun findComment(comments: List<ForumComment>, id: String): ForumComment? {
    for (comment in comments) {
        if (comment.id == id) return comment
        findComment(comment.children, id)?.let { return it }
    }
    return null
}

/** The thread with [change] made to the comment with [id], wherever it sits. */
private fun changeComment(
    comments: List<ForumComment>,
    id: String,
    change: (ForumComment) -> ForumComment,
): List<ForumComment> = comments.map { comment ->
    val updated = if (comment.id == id) change(comment) else comment
    if (updated.children.isEmpty()) updated
    else updated.copy(children = changeComment(updated.children, id, change))
}
