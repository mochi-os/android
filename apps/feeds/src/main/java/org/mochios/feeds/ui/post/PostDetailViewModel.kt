// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.post

import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.feeds.R
import org.mochios.feeds.model.Permissions
import org.mochios.feeds.model.Post
import org.mochios.feeds.model.PublishAccount
import org.mochios.feeds.model.Tag
import org.mochios.feeds.repository.FeedsRepository
import javax.inject.Inject
import org.mochios.feeds.ui.component.applied
import org.mochios.feeds.ui.component.boostedFrom
import org.mochios.feeds.ui.component.hostedFediverse
import org.mochios.feeds.ui.component.repliesElsewhere

@HiltViewModel
class PostDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: FeedsRepository,
    private val webSocket: MochiWebSocket,
    private val sessionManager: SessionManager,
    private val application: Application
) : ViewModel() {

    /** The picked file's real name, for labelling a draft attachment. */
    suspend fun fileName(uri: Uri): String = repository.fileName(uri)

    val feedId: String = savedStateHandle.get<String>("feedId") ?: ""
    val postId: String = savedStateHandle.get<String>("postId") ?: ""
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
        val feed = _post.value?.feedFingerprint?.takeIf { it.isNotEmpty() } ?: feedId
        return "$serverUrl/feeds/$feed/$postId?comment=${Uri.encode(comment)}"
    }

    private val _post = MutableStateFlow<Post?>(null)
    val post: StateFlow<Post?> = _post.asStateFlow()

    private val _permissions = MutableStateFlow(Permissions())
    val permissions: StateFlow<Permissions> = _permissions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<MochiError?>(null)
    val error: StateFlow<MochiError?> = _error.asStateFlow()

    private val _isNotFound = MutableStateFlow(false)
    val isNotFound: StateFlow<Boolean> = _isNotFound.asStateFlow()

    private val _commentText = MutableStateFlow("")
    val commentText: StateFlow<String> = _commentText.asStateFlow()

    private val _commentAttachments = MutableStateFlow<List<Uri>>(emptyList())
    val commentAttachments: StateFlow<List<Uri>> = _commentAttachments.asStateFlow()

    private val _isSendingComment = MutableStateFlow(false)
    val isSendingComment: StateFlow<Boolean> = _isSendingComment.asStateFlow()

    private val commentTracker = UploadTracker()

    /** The files of the comment being sent, for the composer. */
    val commentUpload: StateFlow<Upload?> = commentTracker.state

    private val _replyingTo = MutableStateFlow<String?>(null)
    val replyingTo: StateFlow<String?> = _replyingTo.asStateFlow()

    private val _editingCommentId = MutableStateFlow<String?>(null)
    val editingCommentId: StateFlow<String?> = _editingCommentId.asStateFlow()

    private val _editCommentText = MutableStateFlow("")
    val editCommentText: StateFlow<String> = _editCommentText.asStateFlow()

    private val _tags = MutableStateFlow<List<Tag>>(emptyList())
    val tags: StateFlow<List<Tag>> = _tags.asStateFlow()

    private val _actionError = MutableStateFlow<MochiError?>(null)
    val actionError: StateFlow<MochiError?> = _actionError.asStateFlow()

    /** What an action that worked says, as a string resource. */
    private val _actionMessage = MutableStateFlow<Int?>(null)
    val actionMessage: StateFlow<Int?> = _actionMessage.asStateFlow()

    /**
     * The user's hosted fediverse accounts, read once a post with a copy there
     * or from there is shown: a comment may go out from one, and a boost is
     * made from the first.
     */
    private val _hosted = MutableStateFlow<List<PublishAccount>>(emptyList())
    val hosted: StateFlow<List<PublishAccount>> = _hosted.asStateFlow()

    /** The hosted account a comment also goes out from; empty for none. */
    private val _replyFrom = MutableStateFlow("")
    val replyFrom: StateFlow<String> = _replyFrom.asStateFlow()

    private val _voting = MutableStateFlow(false)
    val voting: StateFlow<Boolean> = _voting.asStateFlow()

    // The viewer's own entity id, so comment items can offer edit/delete on the
    // viewer's own comments. Resolved once from the bound session identity.
    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    private var subscriptionId: String? = null

    /** The pending or in-flight post fetch; cancelled when a newer one starts. */
    private var postJob: Job? = null

    init {
        loadPost()
        subscribeToWebSocket()
        viewModelScope.launch { _currentUserId.value = sessionManager.getBoundIdentity() }
    }

    fun loadPost() {
        postJob?.cancel()
        postJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _isNotFound.value = false
            try {
                val result = repository.getPost(feedId, postId)
                _post.value = result.post
                _permissions.value = result.permissions
                loadTags()
                loadHosted(result.post)
            } catch (e: Exception) {
                ensureActive()
                val err = e.toMochiError()
                _error.value = err
                if (err is MochiError.NotFoundError) _isNotFound.value = true
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Refetch the post in the background, without the spinner: socket frames
     * and our own actions. Cancels the pending fetch and waits
     * [REFRESH_DEBOUNCE] first, so an action and its echo frame make one
     * fetch.
     */
    private fun refreshPost() {
        postJob?.cancel()
        postJob = viewModelScope.launch {
            delay(REFRESH_DEBOUNCE)
            refreshSilently()
        }
    }

    /** Follow or unfollow the post: every reply and reaction in a followed
     *  one notifies. */
    fun setFollowing(following: Boolean) {
        viewModelScope.launch {
            try {
                repository.followPost(feedId, postId, following)
                _post.value = _post.value?.copy(following = following)
            } catch (e: Exception) {
                _actionError.value = e.toMochiError()
            }
        }
    }

    fun reactToPost(reaction: String) {
        viewModelScope.launch {
            try {
                repository.reactToPost(feedId, postId, reaction)
                _post.value = _post.value?.copy(myReaction = applied(reaction))
            } catch (_: Exception) {
                refreshPost()
            }
        }
    }

    fun setCommentText(text: String) {
        _commentText.value = text
    }

    fun addCommentAttachment(uri: Uri) {
        _commentAttachments.value = _commentAttachments.value + uri
    }

    fun removeCommentAttachment(uri: Uri) {
        _commentAttachments.value = _commentAttachments.value - uri
    }

    fun setReplyingTo(commentId: String?) {
        _replyingTo.value = commentId
    }

    /**
     * Sends the draft. From the lightbox's comments panel, [anchor] is the
     * image the comment is about; a reply keeps its parent's context instead.
     */
    fun sendComment(anchor: String? = null) {
        val body = _commentText.value.trim()
        if (body.isEmpty()) return

        viewModelScope.launch {
            _isSendingComment.value = true
            _actionError.value = null
            try {
                val files = _commentAttachments.value
                commentTracker.track(files) {
                    repository.createComment(
                        feedId = feedId,
                        postId = postId,
                        body = body,
                        parent = _replyingTo.value,
                        files = files,
                        context = application,
                        attachment = anchor,
                        tracker = commentTracker,
                        account = _replyFrom.value,
                    )
                    _commentText.value = ""
                    _commentAttachments.value = emptyList()
                    _replyingTo.value = null
                    // Inside the upload, so the files in flight give way to
                    // the comment with nothing blank between.
                    refreshPost()
                }
            } catch (e: Exception) {
                _actionError.value = e.toMochiError()
            } finally {
                _isSendingComment.value = false
            }
        }
    }

    fun startEditComment(commentId: String, currentBody: String) {
        _editingCommentId.value = commentId
        _editCommentText.value = currentBody
    }

    fun cancelEditComment() {
        _editingCommentId.value = null
        _editCommentText.value = ""
    }

    fun setEditCommentText(text: String) {
        _editCommentText.value = text
    }

    fun saveEditComment() {
        val commentId = _editingCommentId.value ?: return
        val body = _editCommentText.value.trim()
        if (body.isEmpty()) return

        viewModelScope.launch {
            _actionError.value = null
            try {
                repository.editComment(feedId, postId, commentId, body)
                _editingCommentId.value = null
                _editCommentText.value = ""
                refreshPost()
            } catch (e: Exception) {
                _actionError.value = e.toMochiError()
            }
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            _actionError.value = null
            try {
                repository.deleteComment(feedId, postId, commentId)
                refreshPost()
            } catch (e: Exception) {
                _actionError.value = e.toMochiError()
            }
        }
    }

    fun reactToComment(commentId: String, reaction: String) {
        viewModelScope.launch {
            try {
                repository.reactToComment(feedId, postId, commentId, reaction)
                refreshPost()
            } catch (_: Exception) {
                // Silent failure for reactions
            }
        }
    }

    fun addTag(label: String, qid: String? = null) {
        viewModelScope.launch {
            _actionError.value = null
            try {
                repository.addTag(feedId, postId, label, qid)
                loadTags()
                refreshPost()
            } catch (e: Exception) {
                _actionError.value = e.toMochiError()
            }
        }
    }


    fun adjustInterest(tag: Tag, direction: String) {
        viewModelScope.launch {
            try {
                repository.adjustInterest(
                    feedId,
                    qid = tag.qid?.takeIf { it.isNotEmpty() },
                    label = if (tag.qid.isNullOrEmpty()) tag.label else null,
                    direction = direction
                )
                applyInterestLocally(tag.qid, direction)
            } catch (e: MochiError) {
                _actionError.value = e
            } catch (_: Exception) {
                // Silent — user can retry
            }
        }
    }

    /**
     * Optimistic local mirror of a server interest adjustment; the deltas and
     * clamp match the server's.
     */
    private fun applyInterestLocally(qid: String?, direction: String) {
        if (qid.isNullOrEmpty()) return
        fun adjust(current: Double?): Double? = when (direction) {
            "up" -> ((current ?: 0.0) + 15.0).coerceAtMost(100.0)
            "down" -> ((current ?: 0.0) - 20.0).coerceAtLeast(-100.0)
            else -> null
        }
        fun update(tags: List<Tag>): List<Tag> =
            tags.map { if (it.qid == qid) it.copy(interest = adjust(it.interest)) else it }
        _tags.value = update(_tags.value)
        _post.value = _post.value?.let { p ->
            if (p.tags.any { it.qid == qid }) p.copy(tags = update(p.tags)) else p
        }
    }

    fun deletePost(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _actionError.value = null
            try {
                repository.deletePost(feedId, postId)
                onSuccess()
            } catch (e: Exception) {
                _actionError.value = e.toMochiError()
            }
        }
    }

    fun clearActionError() {
        _actionError.value = null
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun setReplyFrom(account: String) {
        _replyFrom.value = account
    }

    private fun loadHosted(post: Post) {
        if (!repliesElsewhere(post) || _hosted.value.isNotEmpty()) return
        viewModelScope.launch {
            try {
                _hosted.value = hostedFediverse(repository.networkAccounts())
            } catch (_: Exception) {
                // Without them the post simply offers no account to reply from.
            }
        }
    }

    /** Vote in the post's poll; the poll as recounted replaces the one shown. */
    fun vote(options: List<Int>) {
        if (_voting.value || options.isEmpty()) return
        _voting.value = true
        viewModelScope.launch {
            try {
                val (poll, voted) = repository.votePoll(feedId, postId, options)
                _post.value = _post.value?.let { current ->
                    current.copy(
                        voted = voted.ifEmpty { options },
                        data = poll?.let { current.data?.copy(poll = it) } ?: current.data,
                    )
                }
            } catch (e: Exception) {
                _actionError.value = e.toMochiError()
            } finally {
                _voting.value = false
            }
        }
    }

    /** Pin the post of the owner's feed, or unpin it. */
    fun togglePin() {
        val current = _post.value ?: return
        viewModelScope.launch {
            try {
                val pinned = repository.pinPost(feedId, postId, current.pinned <= 0)
                _post.value = _post.value?.copy(pinned = pinned)
                _actionMessage.value = if (pinned > 0) R.string.feeds_post_pinned else R.string.feeds_post_unpinned
            } catch (e: Exception) {
                _actionError.value = e.toMochiError()
            }
        }
    }

    /** Boost the post from another network from the first hosted account, or take the boost back. */
    fun toggleBoost() {
        val current = _post.value ?: return
        val account = _hosted.value.firstOrNull() ?: return
        val boosted = boostedFrom(current, account)
        viewModelScope.launch {
            try {
                val copies = repository.boostPost(feedId, postId, account.id, !boosted)
                _post.value = _post.value?.copy(copies = copies)
                _actionMessage.value = if (boosted) R.string.feeds_boost_taken_back else R.string.feeds_boosted
            } catch (e: Exception) {
                _actionError.value = e.toMochiError()
            }
        }
    }

    /** Block, mute or report the person on another network behind a comment. */
    fun moderate(commentId: String, action: String, reason: String = "") {
        viewModelScope.launch {
            try {
                repository.moderateCommenter(feedId, postId, commentId, action, reason.trim())
                _actionMessage.value = when (action) {
                    "block" -> R.string.feeds_commenter_blocked
                    "mute" -> R.string.feeds_commenter_muted
                    else -> R.string.feeds_commenter_reported
                }
            } catch (e: Exception) {
                _actionError.value = e.toMochiError()
            }
        }
    }

    suspend fun searchMembers(query: String): List<org.mochios.android.ui.components.MentionSuggestion> {
        return try {
            repository.searchMembers(feedId, query).map {
                org.mochios.android.ui.components.MentionSuggestion(id = it.id, name = it.name)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadTags() {
        viewModelScope.launch {
            try {
                _tags.value = repository.getPostTags(feedId, postId)
            } catch (_: Exception) {
                // Tags are non-critical
            }
        }
    }

    private fun subscribeToWebSocket() {
        if (feedId.isEmpty()) return
        val serverUrl = sessionManager.getServerUrlBlocking()
        subscriptionId = webSocket.subscribe(serverUrl, feedId, app = "feeds") { event ->
            if (event.post == postId) {
                // Server event types are slash-namespaced (feeds.star commit
                // hook + handlers); the old underscore names never matched.
                when (event.type) {
                    "comment/create", "comment/delete", "comment/edit",
                    "react/post", "react/comment", "post/edit",
                    "tag/add", "tag/remove" -> {
                        refreshPost()
                    }
                }
            }
        }
    }

    private suspend fun refreshSilently() {
        try {
            val result = repository.getPost(feedId, postId)
            _post.value = result.post
            _permissions.value = result.permissions
            loadHosted(result.post)
        } catch (_: Exception) {
            // Silent refresh
        }
    }

    override fun onCleared() {
        super.onCleared()
        subscriptionId?.let { webSocket.unsubscribe(it) }
    }
}

/** The post route's argument naming the comment it is opened on. */
private const val COMMENT = "comment"
