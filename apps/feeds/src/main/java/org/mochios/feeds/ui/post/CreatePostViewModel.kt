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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.files.Upload
import org.mochios.android.files.UploadTracker
import org.mochios.android.model.Attachment
import org.mochios.android.model.PlaceData
import org.mochios.android.util.NaturalCompare
import org.mochios.feeds.R
import org.mochios.android.i18n.LanguageStore
import org.mochios.feeds.model.Feed
import org.mochios.feeds.model.PollInput
import org.mochios.feeds.model.PostFeatures
import org.mochios.feeds.model.PublishState
import org.mochios.feeds.repository.FeedsRepository
import org.mochios.feeds.ui.component.POLL_DURATIONS
import org.mochios.feeds.ui.component.POLL_OPTIONS_MAXIMUM
import org.mochios.feeds.ui.component.POLL_OPTIONS_MINIMUM
import org.mochios.feeds.ui.component.POLL_OPTION_MAXIMUM
import org.mochios.feeds.ui.component.PollDraft
import org.mochios.feeds.ui.component.VISIBILITIES
import org.mochios.feeds.ui.component.WARNING_MAXIMUM
import org.mochios.feeds.ui.component.pollValid
import java.util.Locale
import javax.inject.Inject

/**
 * The attachment order an edit sends: the kept existing attachments in their
 * original order, then "new:N" for each new file. Null until the post's
 * attachments have loaded, so a save made before then cannot read as removing
 * them all.
 */
internal fun editOrder(loaded: Boolean, existing: List<String>, removed: Set<String>, added: Int): List<String>? =
    if (loaded) existing.filter { it !in removed } + List(added) { "new:$it" } else null

@HiltViewModel
class CreatePostViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FeedsRepository,
    private val application: Application
) : ViewModel() {

    /** The picked file's real name, for labelling a draft attachment. */
    suspend fun fileName(uri: Uri): String = repository.fileName(uri)

    private val preSelectedFeedId: String? = savedStateHandle.get<String>("feedId")
    private val editingPostId: String? = savedStateHandle.get<String>("postId")
    val isEditing: Boolean = editingPostId != null

    // True when we arrived from a specific feed (via its FAB / empty state), so
    // the destination is already known and no feed picker should be shown.
    val feedPreselected: Boolean = !preSelectedFeedId.isNullOrEmpty()

    private val _availableFeeds = MutableStateFlow<List<Feed>>(emptyList())
    val availableFeeds: StateFlow<List<Feed>> = _availableFeeds.asStateFlow()

    private val _selectedFeed = MutableStateFlow(preSelectedFeedId ?: "")
    val selectedFeed: StateFlow<String> = _selectedFeed.asStateFlow()

    private val _body = MutableStateFlow("")
    val body: StateFlow<String> = _body.asStateFlow()

    private val _attachments = MutableStateFlow<List<Uri>>(emptyList())
    val attachments: StateFlow<List<Uri>> = _attachments.asStateFlow()

    private val _existingAttachments = MutableStateFlow<List<Attachment>>(emptyList())
    val existingAttachments: StateFlow<List<Attachment>> = _existingAttachments.asStateFlow()

    private val _removedExistingIds = MutableStateFlow<Set<String>>(emptySet())
    // Whether the edited post's attachments have loaded.
    private var loaded = false
    val removedExistingIds: StateFlow<Set<String>> = _removedExistingIds.asStateFlow()

    // Captions keyed by the new file's Uri and the saved attachment's id, so
    // neither removal nor reorder can re-attach a caption to the wrong item.
    private val _newCaptions = MutableStateFlow<Map<Uri, String>>(emptyMap())
    val newCaptions: StateFlow<Map<Uri, String>> = _newCaptions.asStateFlow()

    private val _existingCaptions = MutableStateFlow<Map<String, String>>(emptyMap())
    val existingCaptions: StateFlow<Map<String, String>> = _existingCaptions.asStateFlow()

    private val _checkin = MutableStateFlow<PlaceData?>(null)
    val checkin: StateFlow<PlaceData?> = _checkin.asStateFlow()

    private val _travellingOrigin = MutableStateFlow<PlaceData?>(null)
    val travellingOrigin: StateFlow<PlaceData?> = _travellingOrigin.asStateFlow()

    private val _travellingDestination = MutableStateFlow<PlaceData?>(null)
    val travellingDestination: StateFlow<PlaceData?> = _travellingDestination.asStateFlow()

    private val _isPosting = MutableStateFlow(false)
    val isPosting: StateFlow<Boolean> = _isPosting.asStateFlow()

    private val tracker = UploadTracker()

    /** The new files of the post being sent, while they go. */
    val upload: StateFlow<Upload?> = tracker.state

    private val _isLoadingFeeds = MutableStateFlow(false)
    val isLoadingFeeds: StateFlow<Boolean> = _isLoadingFeeds.asStateFlow()

    private val _error = MutableStateFlow<MochiError?>(null)
    val error: StateFlow<MochiError?> = _error.asStateFlow()

    private val _postSuccess = MutableStateFlow(false)
    val postSuccess: StateFlow<Boolean> = _postSuccess.asStateFlow()

    /**
     * Where the chosen feed publishes: offered as switches on a public feed the
     * writer owns, preset from the feed's list. [accounts] is null until the
     * writer changes a switch, which leaves the choice to the feed.
     */
    private val _publish = MutableStateFlow<PublishState?>(null)
    val publish: StateFlow<PublishState?> = _publish.asStateFlow()

    private val _accounts = MutableStateFlow<List<String>?>(null)
    val accounts: StateFlow<List<String>?> = _accounts.asStateFlow()

    /** Who may see the post, its content warning and its sensitive flag. */
    private val _visibility = MutableStateFlow("public")
    val visibility: StateFlow<String> = _visibility.asStateFlow()

    private val _warning = MutableStateFlow("")
    val warning: StateFlow<String> = _warning.asStateFlow()

    private val _sensitive = MutableStateFlow(false)
    val sensitive: StateFlow<Boolean> = _sensitive.asStateFlow()

    /** The poll the post is made with; a post being edited keeps its own. */
    private val _poll = MutableStateFlow<PollDraft?>(null)
    val poll: StateFlow<PollDraft?> = _poll.asStateFlow()

    private var publishJob: Job? = null

    init {
        loadAvailableFeeds()
        if (isEditing) {
            loadExistingPost()
        }
    }

    private fun loadAvailableFeeds() {
        viewModelScope.launch {
            _isLoadingFeeds.value = true
            try {
                val feeds = if (!preSelectedFeedId.isNullOrEmpty()) {
                    repository.getNewPostFeeds(preSelectedFeedId)
                } else {
                    repository.listFeeds().filter { it.owner == 1 }
                }.sortedWith(compareBy(NaturalCompare) { it.name })
                _availableFeeds.value = feeds
                if (_selectedFeed.value.isEmpty() && feeds.isNotEmpty()) {
                    _selectedFeed.value = feeds.first().fingerprint.ifEmpty { feeds.first().id }
                }
                loadPublish()
            } catch (_: Exception) {
            } finally {
                _isLoadingFeeds.value = false
            }
        }
    }

    private fun loadExistingPost() {
        val feedId = preSelectedFeedId ?: return
        val postId = editingPostId ?: return
        viewModelScope.launch {
            try {
                val result = repository.getPost(feedId, postId)
                _body.value = result.post.body
                _existingAttachments.value = result.post.attachments
                _existingCaptions.value = result.post.attachments
                    .filter { it.caption.isNotEmpty() }
                    .associate { it.id to it.caption }
                loaded = true
                _visibility.value = result.post.visibility.ifEmpty { "public" }
                _warning.value = result.post.warning
                _sensitive.value = result.post.sensitive != 0
                // Load location data from post.data if present
                result.post.data?.checkin?.let { _checkin.value = it }
                result.post.data?.travelling?.origin?.let { _travellingOrigin.value = it }
                result.post.data?.travelling?.destination?.let { _travellingDestination.value = it }
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            }
        }
    }

    fun toggleRemoveExistingAttachment(id: String) {
        val current = _removedExistingIds.value
        _removedExistingIds.value = if (id in current) current - id else current + id
    }

    /** An empty caption removes the entry; saving empty is how a caption is cleared. */
    fun setAttachmentCaption(uri: Uri, caption: String) {
        _newCaptions.value =
            if (caption.isEmpty()) _newCaptions.value - uri
            else _newCaptions.value + (uri to caption)
    }

    fun setExistingAttachmentCaption(id: String, caption: String) {
        _existingCaptions.value =
            if (caption.isEmpty()) _existingCaptions.value - id
            else _existingCaptions.value + (id to caption)
    }

    fun setSelectedFeed(feedId: String) {
        _selectedFeed.value = feedId
        loadPublish()
    }

    /** The chosen feed's publish list, for a public feed of a new post. */
    private fun loadPublish() {
        publishJob?.cancel()
        _publish.value = null
        _accounts.value = null
        val feedId = _selectedFeed.value
        val feed = _availableFeeds.value.firstOrNull { it.fingerprint == feedId || it.id == feedId }
        if (isEditing || feedId.isEmpty() || feed?.privacy != "public") return
        publishJob = viewModelScope.launch {
            try {
                _publish.value = repository.getPublish(feedId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Without it the post goes where the feed's own list says.
            }
        }
    }

    /** Switch publishing to one account on or off for this post alone. */
    fun setPublishing(account: String, on: Boolean) {
        val chosen = _accounts.value ?: _publish.value?.publish.orEmpty()
        _accounts.value = if (on) (chosen + account).distinct() else chosen - account
    }

    fun setVisibility(value: String) {
        if (value in VISIBILITIES) _visibility.value = value
    }

    fun setWarning(value: String) {
        _warning.value = value.take(WARNING_MAXIMUM)
    }

    fun setSensitive(value: Boolean) {
        _sensitive.value = value
    }

    fun startPoll() {
        if (_poll.value == null && !isEditing) _poll.value = PollDraft()
    }

    fun removePoll() {
        _poll.value = null
    }

    fun setPollOption(index: Int, text: String) {
        val poll = _poll.value ?: return
        if (index !in poll.options.indices) return
        _poll.value = poll.copy(options = poll.options.toMutableList().also { it[index] = text.take(POLL_OPTION_MAXIMUM) })
    }

    fun addPollOption() {
        val poll = _poll.value ?: return
        if (poll.options.size < POLL_OPTIONS_MAXIMUM) _poll.value = poll.copy(options = poll.options + "")
    }

    fun removePollOption(index: Int) {
        val poll = _poll.value ?: return
        if (poll.options.size > POLL_OPTIONS_MINIMUM && index in poll.options.indices) {
            _poll.value = poll.copy(options = poll.options.filterIndexed { i, _ -> i != index })
        }
    }

    fun setPollMultiple(multiple: Boolean) {
        _poll.value = _poll.value?.copy(multiple = multiple)
    }

    fun setPollDuration(duration: Int) {
        if (duration in POLL_DURATIONS) _poll.value = _poll.value?.copy(duration = duration)
    }

    fun setBody(text: String) {
        _body.value = text
    }

    fun addAttachments(uris: List<Uri>) {
        _attachments.value = _attachments.value + uris
    }

    fun removeAttachment(uri: Uri) {
        _attachments.value = _attachments.value - uri
    }

    fun moveExistingAttachment(attachmentId: String, direction: Int) {
        val current = _existingAttachments.value.toMutableList()
        val index = current.indexOfFirst { it.id == attachmentId }
        if (index < 0) return
        val newIndex = (index + direction).coerceIn(0, current.size - 1)
        if (newIndex == index) return
        val tmp = current[index]
        current[index] = current[newIndex]
        current[newIndex] = tmp
        _existingAttachments.value = current
    }

    fun moveAttachment(uri: Uri, direction: Int) {
        val list = _attachments.value.toMutableList()
        val index = list.indexOf(uri)
        if (index < 0) return
        val newIndex = (index + direction).coerceIn(0, list.lastIndex)
        if (newIndex == index) return
        list.removeAt(index)
        list.add(newIndex, uri)
        _attachments.value = list
    }

    /** Commit a check-in, replacing any travelling data. */
    fun applyCheckin(place: PlaceData?) {
        _checkin.value = place
        if (place != null) {
            _travellingOrigin.value = null
            _travellingDestination.value = null
        }
    }

    /** Commit a travelling origin/destination pair, replacing any check-in. */
    fun applyTravelling(origin: PlaceData?, destination: PlaceData?) {
        _travellingOrigin.value = origin
        _travellingDestination.value = destination
        if (origin != null || destination != null) {
            _checkin.value = null
        }
    }

    fun clearLocation() {
        _checkin.value = null
        _travellingOrigin.value = null
        _travellingDestination.value = null
    }

    fun createPost() {
        val feedId = _selectedFeed.value
        if (feedId.isEmpty()) {
            _error.value = MochiError.Local(R.string.feeds_post_select_feed)
            return
        }
        val bodyText = _body.value.trim()
        val hasContent = bodyText.isNotEmpty() ||
                _attachments.value.isNotEmpty() ||
                _existingAttachments.value.any { it.id !in _removedExistingIds.value }
        if (!hasContent) {
            _error.value = MochiError.Local(R.string.feeds_post_empty_content)
            return
        }
        if (!pollValid(_poll.value)) return
        val features = PostFeatures(
            visibility = _visibility.value,
            warning = _warning.value.trim(),
            sensitive = _sensitive.value,
            // The post's language is the writer's own, as the app runs in it.
            language = if (isEditing) null else writerLanguage(),
        )

        viewModelScope.launch {
            _isPosting.value = true
            _error.value = null
            try {
                if (isEditing) {
                    val postId = editingPostId!!
                    val keptExisting = _existingAttachments.value
                        .filter { it.id !in _removedExistingIds.value }
                        .map { it.id }
                    val order = editOrder(
                        loaded,
                        _existingAttachments.value.map { it.id },
                        _removedExistingIds.value,
                        _attachments.value.size
                    )
                    val newFileUris = _attachments.value
                    // Caption edits keyed by order entry. Kept ids always
                    // appear, so clearing a caption reaches the server as an
                    // empty string; placeholders only when captioned.
                    val captions = buildMap {
                        keptExisting.forEach { id -> put(id, _existingCaptions.value[id] ?: "") }
                        newFileUris.forEachIndexed { index, uri ->
                            _newCaptions.value[uri]?.takeIf { it.isNotEmpty() }
                                ?.let { put("new:$index", it) }
                        }
                    }
                    tracker.track(newFileUris) {
                        repository.editPost(
                            feedId = feedId,
                            postId = postId,
                            body = bodyText,
                            order = order,
                            newFiles = newFileUris,
                            captions = captions,
                            context = application,
                            checkin = _checkin.value,
                            travellingOrigin = _travellingOrigin.value,
                            travellingDestination = _travellingDestination.value,
                            tracker = tracker,
                            features = features,
                        )
                    }
                } else {
                    val uris = _attachments.value
                    tracker.track(uris) {
                        repository.createPostFromUris(
                            feedId = feedId,
                            body = bodyText,
                            uris = uris,
                            captions = uris.map { _newCaptions.value[it] ?: "" },
                            context = application,
                            checkin = _checkin.value,
                            travellingOrigin = _travellingOrigin.value,
                            travellingDestination = _travellingDestination.value,
                            tracker = tracker,
                            accounts = _accounts.value,
                            features = features,
                            poll = _poll.value?.let { draft ->
                                PollInput(
                                    options = draft.options.map { it.trim() },
                                    multiple = draft.multiple,
                                    duration = draft.duration,
                                )
                            },
                        )
                    }
                }
                _postSuccess.value = true
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            } finally {
                _isPosting.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun writerLanguage(): String =
        (LanguageStore.get(application) ?: Locale.getDefault().toLanguageTag()).substringBefore('-').lowercase()

    suspend fun searchMembers(query: String): List<org.mochios.android.ui.components.MentionSuggestion> {
        val feedId = _selectedFeed.value
        if (feedId.isEmpty()) return emptyList()
        return try {
            repository.searchMembers(feedId, query).map {
                org.mochios.android.ui.components.MentionSuggestion(id = it.id, name = it.name)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
