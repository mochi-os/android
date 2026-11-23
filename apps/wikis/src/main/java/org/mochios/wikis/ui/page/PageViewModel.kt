// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.page

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.model.WebSocketEvent
import org.mochios.android.share.Shared
import org.mochios.android.util.REFRESH_DEBOUNCE
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.REOPEN
import org.mochios.wikis.R
import org.mochios.wikis.model.PageFetchResponse
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.model.WikiPage
import org.mochios.wikis.model.WikiPermissions
import org.mochios.wikis.repository.WikisRepository
import org.mochios.wikis.ui.components.WikiInfoLoader
import javax.inject.Inject

sealed class PageViewEvent {
    /**
     * Copy this URL to the clipboard and confirm it. [replaced] is true when
     * the user asked for a new URL, retiring the one issued before.
     */
    data class CopyRssUrl(val url: String, val replaced: Boolean = false) : PageViewEvent()

    /**
     * A feed URL for this [mode] was already issued, and the server keeps only
     * its hash, so it cannot be shown again. Offer to replace it - which stops
     * the old URL resolving, so it is the user's call, not ours.
     */
    data class RssExists(val mode: String) : PageViewEvent()

    /** The wiki's RSS token was cleared; confirm with a snackbar. */
    data object RssRevoked : PageViewEvent()

    /** Display a transient error toast (already localised). */
    data class ShowError(val error: MochiError) : PageViewEvent()

    /**
     * Say [messageRes], formatted with [args]; for a failure, the server's own
     * reason in [error] where it sent one.
     */
    data class Notice(
        val messageRes: Int,
        val args: List<Any> = emptyList(),
        val error: MochiError? = null,
    ) : PageViewEvent()

    /** The page is gone; the screen showing it has to leave. */
    object Deleted : PageViewEvent()
}

data class PageViewUiState(
    val isLoading: Boolean = true,
    val page: WikiPage? = null,
    val missingLinks: List<String> = emptyList(),
    val commentCount: Int = 0,
    val wiki: WikiInfo? = null,
    val permissions: WikiPermissions = WikiPermissions(),
    val error: MochiError? = null,
    val notFound: Boolean = false,
    val isDeleting: Boolean = false,
)

@HiltViewModel
class PageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WikisRepository,
    private val sessionManager: SessionManager,
    private val webSocket: MochiWebSocket,
) : ViewModel() {

    val wikiId: String = savedStateHandle.get<String>("wikiId").orEmpty()
    val slug: String = savedStateHandle.get<String>("page").orEmpty()

    /** Origin of the Mochi server the session is bound to. Trimmed of trailing slash. */
    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

    private val _uiState = MutableStateFlow(PageViewUiState())
    val uiState: StateFlow<PageViewUiState> = _uiState.asStateFlow()

    /** The wikis the user holds, owned or subscribed, as the drawer lists them. */
    val wikis: StateFlow<List<WikiInfo>> = repository.wikiList

    private val _events = MutableSharedFlow<PageViewEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PageViewEvent> = _events.asSharedFlow()

    private val _shares = MutableSharedFlow<Shared>(extraBufferCapacity = 1)

    /** The page, or the wiki, readied for the system share sheet. */
    val shares: SharedFlow<Shared> = _shares.asSharedFlow()

    private var subscription: String? = null

    /** The pending or in-flight quiet reload; cancelled when a newer one starts. */
    private var refreshJob: Job? = null

    /** Set while another screen, such as the comments, covers this one. */
    @Volatile
    private var hidden = false

    /** A frame arrived while [hidden]; coming back reloads once for all of them. */
    @Volatile
    private var missed = false

    /**
     * A frame waiting on [refreshQuietly], or [missed] while [hidden], did not
     * say what changed, so that reload reads the wiki's info as well as the page.
     */
    @Volatile
    private var infoPending = false

    /** The page was deleted here; frames about it are no longer this screen's. */
    @Volatile
    private var gone = false

    /**
     * The wiki and what the reader may do in it, asked for with the page until
     * it arrives. Its arrival opens the wiki's live updates.
     */
    private val information = WikiInfoLoader(
        fetch = { repository.getInfo(wikiId) },
        loaded = { response ->
            _uiState.value = _uiState.value.copy(
                wiki = response.wiki,
                permissions = response.permissions ?: WikiPermissions(),
            )
            watch(response.wiki?.fingerprint ?: wikiId)
        },
    )

    init {
        loadPage()
    }

    override fun onCleared() {
        super.onCleared()
        subscription?.let { webSocket.unsubscribe(it) }
    }

    /**
     * Watch the wiki for content that lands from P2P sync or a live broadcast.
     * The server writes on the wiki's fingerprint, which is what the route
     * usually carries; an entity-id deep link needs the resolved one from the
     * info response, so this runs once the info is in. Idempotent - a reload
     * of the info must not open a second socket.
     */
    private fun watch(fingerprint: String) {
        if (subscription != null || fingerprint.isEmpty()) {
            return
        }
        subscription = webSocket.subscribe(serverUrl, fingerprint, app = "wikis") { event ->
            handle(event)
        }
    }

    /**
     * A change in the wiki. The page reads itself again only when the change
     * concerns it, or when what changed is not said: a settings change, a
     * renamed wiki or a sync dump, an older server, a resync, and a reopened
     * socket, which missed whatever was sent meanwhile.
     */
    private fun handle(event: WebSocketEvent) {
        when (event.type) {
            "wiki/resynced", REOPEN -> reloadForFrame(withInfo = true)
            "wiki/update" -> {
                if (!wikiUpdateNamed(event.kind)) {
                    reloadForFrame(withInfo = true)
                } else if (pageConcerned(event.kind, event.page, slug, _uiState.value.page?.slug)) {
                    reloadForFrame(withInfo = false)
                }
            }
        }
    }

    private fun reloadForFrame(withInfo: Boolean) {
        if (gone) {
            return
        }
        if (withInfo) {
            infoPending = true
        }
        if (hidden) {
            missed = true
        } else {
            refreshQuietly()
        }
    }

    /** The screen's ON_PAUSE: frames wait for [onScreenResumed] instead of reloading. */
    fun onScreenPaused() {
        hidden = true
    }

    /** The screen's ON_RESUME: one reload for any frame that arrived while it was covered. */
    fun onScreenResumed() {
        hidden = false
        if (missed && !gone) {
            missed = false
            refreshQuietly()
        }
    }

    /**
     * Reload the page without the spinner, and the wiki's info first when
     * [infoPending] asks for it. Each call cancels the last and waits
     * [REFRESH_DEBOUNCE], so a burst of frames makes one fetch of each. The
     * flag is cleared only once the info is read, so a reload cancelled
     * midway hands it on to the next.
     */
    private fun refreshQuietly() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(REFRESH_DEBOUNCE)
            try {
                if (infoPending) {
                    val info = repository.getInfo(wikiId)
                    infoPending = false
                    _uiState.value = _uiState.value.copy(
                        wiki = info.wiki,
                        permissions = info.permissions ?: WikiPermissions(),
                    )
                }
                val response = repository.getPage(wikiId, slug)
                if (!gone) {
                    show(response)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    /** Put a page answer on screen: the page, or that it does not exist. */
    private fun show(response: PageFetchResponse) {
        when (response) {
            is PageFetchResponse.Page -> {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    page = response.page,
                    missingLinks = response.missingLinks ?: emptyList(),
                    commentCount = response.commentCount ?: 0,
                    notFound = false,
                    error = null,
                )
            }
            is PageFetchResponse.NotFound -> {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    page = null,
                    missingLinks = emptyList(),
                    commentCount = 0,
                    notFound = true,
                    error = null,
                )
            }
        }
    }

    /**
     * Load the wiki's information, until it has arrived, and then the page:
     * the screen's load and its Retry, so an information request that failed
     * is asked again rather than leaving the page without its permissions or
     * its live updates.
     */
    fun loadPage() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, notFound = false, error = null)
            try {
                information.ensure()
                val response = repository.getPage(wikiId, slug)
                show(response)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    /**
     * Delete the page this screen is showing. Confirmed by the dialog first —
     * this is the yes, not the question.
     */
    fun delete() {
        if (_uiState.value.isDeleting) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true)
            try {
                repository.deletePage(wikiId, slug)
                gone = true
                refreshJob?.cancel()
                subscription?.let { id -> webSocket.unsubscribe(id) }
                subscription = null
                _uiState.value = _uiState.value.copy(isDeleting = false)
                _events.emit(PageViewEvent.Deleted)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isDeleting = false)
                _events.emit(PageViewEvent.ShowError(e.toMochiError()))
            }
        }
    }

    suspend fun unsubscribe() {
        repository.unsubscribeWiki(wikiId)
    }

    fun copyRssUrl(mode: String, regenerate: Boolean = false) {
        viewModelScope.launch {
            try {
                val answer = repository.wikiRssToken(wikiId, mode, regenerate)
                if (answer.exists) {
                    _events.emit(PageViewEvent.RssExists(mode))
                    return@launch
                }
                val url = "$serverUrl/wikis/$wikiId/rss?token=${answer.token}"
                _events.emit(PageViewEvent.CopyRssUrl(url, regenerate))
            } catch (e: Exception) {
                _events.emit(PageViewEvent.ShowError(e.toMochiError()))
            }
        }
    }

    /** The wiki's page here, by its fingerprint where it has one. */
    private fun wikiAddress(): String =
        "$serverUrl/wikis/${_uiState.value.wiki?.fingerprint?.takeIf { it.isNotEmpty() } ?: wikiId}"

    /**
     * Share the page shown: its title and the start of its text, and its
     * address here. Anyone who can read it may share it.
     */
    fun sharePage() {
        val page = _uiState.value.page ?: return
        val slug = page.slug.ifBlank { slug }
        _shares.tryEmit(Shared(text = page.title, url = wikiAddress() + "/" + Uri.encode(slug, "/")))
    }

    /**
     * Share the wiki: by its name and address, and to other apps by the
     * `mochi://` link another server joins it by, which the server assembles.
     */
    fun shareWiki() {
        if (wikiId.isBlank()) return
        viewModelScope.launch {
            try {
                val link = repository.shareWiki(wikiId)
                _shares.emit(Shared(text = _uiState.value.wiki?.name.orEmpty(), url = wikiAddress(), link = link))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(PageViewEvent.ShowError(e.toMochiError()))
            }
        }
    }

    /**
     * Clear the wiki's RSS token, so every URL already handed out stops
     * working. Also the only way to rotate one: minting returns the existing
     * token unchanged.
     */
    fun revokeRssToken() {
        viewModelScope.launch {
            try {
                repository.revokeRssToken(wikiId)
                _events.emit(PageViewEvent.RssRevoked)
            } catch (e: Exception) {
                _events.emit(PageViewEvent.ShowError(e.toMochiError()))
            }
        }
    }

    /** The page's slug as loaded, which a redirect may have made other than the route's. */
    private fun pageSlug(): String = _uiState.value.page?.slug?.takeIf { value -> value.isNotBlank() } ?: slug

    /** Show [tags] as the page's, on the page as it stands by then. */
    private fun showTags(tags: List<String>) {
        val current = _uiState.value.page ?: return
        _uiState.value = _uiState.value.copy(page = current.copy(tags = tags))
    }

    /**
     * Tag the page. The chip appears once the server has taken it, among the
     * tags shown by then, which a reload may have changed meanwhile.
     */
    fun addTag(tag: String) {
        if (_uiState.value.page == null) return
        val target = pageSlug()
        viewModelScope.launch {
            try {
                repository.addTag(wikiId, target, tag)
                showTags((_uiState.value.page?.tags.orEmpty() + tag).distinct())
                _events.emit(PageViewEvent.Notice(R.string.wikis_tag_manager_added, listOf(tag)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(PageViewEvent.Notice(R.string.wikis_tag_manager_add_failed, error = e.toMochiError()))
            }
        }
    }

    /**
     * Untag the page. The chip goes on the tap and comes back, with the
     * error, if the server refuses, in its old place among the tags shown by
     * then.
     */
    fun removeTag(tag: String) {
        val before = _uiState.value.page?.tags ?: return
        val target = pageSlug()
        showTags(before.filterNot { other -> other.equals(tag, ignoreCase = true) })
        viewModelScope.launch {
            try {
                repository.removeTag(wikiId, target, tag)
                _events.emit(PageViewEvent.Notice(R.string.wikis_tag_manager_removed, listOf(tag)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val latest = _uiState.value.page?.tags.orEmpty()
                if (latest.none { other -> other.equals(tag, ignoreCase = true) }) {
                    val order = before.withIndex().associate { (index, other) -> other.lowercase() to index }
                    showTags((latest + tag).sortedBy { other -> order[other.lowercase()] ?: Int.MAX_VALUE })
                }
                _events.emit(PageViewEvent.Notice(R.string.wikis_tag_manager_remove_failed, error = e.toMochiError()))
            }
        }
    }
}

/**
 * Whether a wiki/update frame says what changed, in a kind this client knows.
 * One that does not - no kind, or one a newer server added - may be anything,
 * so everything is read again.
 */
internal fun wikiUpdateNamed(kind: String?): Boolean = kind in WIKI_UPDATE_KINDS

private val WIKI_UPDATE_KINDS = setOf("edit", "pages", "comment", "tag", "redirect", "attachment")

/**
 * Whether a wiki/update frame of a known [kind] concerns the page opened by
 * [opened], whose slug as loaded is [loaded]: a page reached through a
 * redirect is opened by one slug and has another.
 */
internal fun pageConcerned(kind: String?, page: String?, opened: String, loaded: String?): Boolean =
    when (kind) {
        // The page's content, title, comment count or tags.
        "edit", "comment", "tag" -> !page.isNullOrEmpty() && (page == opened || page == loaded)
        // A page created, deleted or renamed, or a redirect set or removed,
        // changes which links are missing on any page (a redirect's slug
        // counts as a page that exists), and the redirect may be the one the
        // page was opened through; a deleted attachment is cut from the pages
        // using it.
        "pages", "redirect", "attachment" -> true
        else -> false
    }
