// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.util.NaturalCompare
import org.mochios.feeds.R
import org.mochios.feeds.model.Backups
import org.mochios.feeds.model.Filter
import org.mochios.feeds.model.PublishAccount
import org.mochios.feeds.model.PublishState
import org.mochios.feeds.repository.FeedsRepository
import java.net.URLEncoder
import javax.inject.Inject
import org.mochios.android.R as MochiR

/** What the publishing and filters sections tell the owner, for the screen to put in words. */
sealed interface PublishEvent {
    /** An action failed; [fallback] names it where the error says nothing. */
    data class Failure(val error: MochiError, val fallback: Int) : PublishEvent

    data class Deleted(val count: Int) : PublishEvent

    data class Generator(val on: Boolean) : PublishEvent

    /** An import from an account: how many so far, and whether more follow. */
    data class Imported(val kind: String, val count: Int, val more: Boolean) : PublishEvent
}

/** The copies a feed keeps on an account that just left its list, offered for deletion. */
data class KeptCopies(val account: PublishAccount, val count: Int)

/**
 * The archive files kept for an account; [files] is null while they load.
 * [repository] says why the last backup kept no repository, if it kept none.
 */
data class AccountBackups(val account: PublishAccount, val files: List<String>?, val repository: String = "")

/**
 * Where a public feed publishes - the owner's accounts on other networks, each
 * switched on or off for the feed, with each account's history imported and
 * its archive downloaded - and the reader's keyword filters on the feed.
 */
@HiltViewModel
class PublishingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FeedsRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val feedId: String = savedStateHandle.get<String>("feedId") ?: ""

    private val _publish = MutableStateFlow<PublishState?>(null)
    val publish: StateFlow<PublishState?> = _publish.asStateFlow()

    /** The account, or "filter" and filter id, whose change is on its way. */
    /** What changes are waiting or running for: an account, a filter, or "filter" for an add. */
    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    val busy: StateFlow<Set<String>> = _busy.asStateFlow()

    private val _kept = MutableStateFlow<KeptCopies?>(null)
    val kept: StateFlow<KeptCopies?> = _kept.asStateFlow()

    private val _backups = MutableStateFlow<AccountBackups?>(null)
    val backups: StateFlow<AccountBackups?> = _backups.asStateFlow()

    private val _filters = MutableStateFlow<List<Filter>>(emptyList())
    val filters: StateFlow<List<Filter>> = _filters.asStateFlow()

    private val _events = MutableSharedFlow<PublishEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PublishEvent> = _events.asSharedFlow()

    /** The accounts in the order shown: by address. */
    fun accounts(state: PublishState?): List<PublishAccount> =
        state?.accounts.orEmpty().sortedWith(compareBy(NaturalCompare) { it.identifier })

    /** Read where the feed publishes, for a public feed the user owns. */
    // The server's refusal to publish the feed anywhere: a private feed's,
    // which says only a public feed publishes, for Publish to to show.
    private val _refusal = MutableStateFlow<MochiError?>(null)
    val refusal: StateFlow<MochiError?> = _refusal.asStateFlow()

    fun loadPublish() {
        viewModelScope.launch {
            try {
                _publish.value = repository.getPublish(feedId)
                _refusal.value = null
            } catch (e: MochiError.ForbiddenError) {
                _publish.value = null
                _refusal.value = e
            } catch (_: Exception) {
                // A server without the networks says nothing of them.
            }
        }
    }

    fun loadFilters() {
        viewModelScope.launch {
            try {
                _filters.value = sorted(repository.getFilters(feedId))
            } catch (_: Exception) {
                // No filters to show.
            }
        }
    }

    /** Switch the feed's publishing to [account] on or off; turning it off offers to delete the copies kept there. */
    fun toggle(account: PublishAccount, on: Boolean) {
        if (_publish.value == null) return
        change(account.id, R.string.feeds_publish_failed) {
            // The list as it stands when this runs: a toggle queued behind
            // another starts from what that one saved.
            val state = _publish.value ?: return@change
            val next = if (on) (state.publish + account.id).distinct() else state.publish - account.id
            val saved = repository.setPublish(feedId, next)
            _publish.value = (_publish.value ?: state).copy(publish = saved.publish)
            val count = saved.kept[account.id] ?: 0
            if (!on && count > 0) _kept.value = KeptCopies(account, count)
        }
    }

    fun keep() {
        _kept.value = null
    }

    /** Delete the copies the feed published on an account it no longer publishes to. */
    fun forget(account: PublishAccount) {
        _kept.value = null
        change(account.id, R.string.feeds_copies_delete_failed) {
            val deleted = repository.forgetCopies(feedId, account.id)
            _events.emit(PublishEvent.Deleted(deleted))
            _publish.value = repository.getPublish(feedId)
        }
    }

    /** Publish the feed as a Bluesky custom feed from a hosted Bluesky account, or stop. */
    fun generator(account: PublishAccount, on: Boolean) {
        change(account.id, R.string.feeds_generator_failed) {
            val generators = repository.setGenerator(feedId, account.id, on)
            _publish.value = _publish.value?.copy(generators = generators)
            _events.emit(PublishEvent.Generator(on))
        }
    }

    /** Bring an account's posts, bookmarks or follows into the feed. */
    fun importFrom(account: PublishAccount, kind: String) {
        change(account.id, R.string.feeds_import_failed) {
            val answer = repository.importAccount(feedId, account.id, kind)
            _events.emit(PublishEvent.Imported(kind, answer.imported, answer.more))
            // People on a server the app may not read yet: the user is asked
            // for each server now, and those allowed are added, the account
            // shown busy meanwhile.
            if (answer.waiting.isNotEmpty()) _asking.value = account.id
            asker.ask(answer.waiting.map { it.server })
        }
    }

    private val asker = WaitingAsker(
        viewModelScope, repository, feedId,
        added = { answer ->
            viewModelScope.launch { _events.emit(PublishEvent.Imported("follows", answer.imported, answer.more)) }
        },
        failed = { e ->
            viewModelScope.launch { _events.emit(PublishEvent.Failure(e.toMochiError(), R.string.feeds_import_failed)) }
        },
    )

    /** The permission a server people wait on needs, while the user is asked for it. */
    val waitingPermission: StateFlow<PendingPermission?> = asker.pending

    /** Whether people an import left waiting are being added, and from which account's import. */
    val addingWaiting: StateFlow<Boolean> = asker.working
    private val _asking = MutableStateFlow<String?>(null)
    val asking: StateFlow<String?> = _asking.asStateFlow()

    fun allowWaitingPermission() = asker.allow()

    fun denyWaitingPermission() = asker.decline()

    fun openBackups(account: PublishAccount) {
        _backups.value = AccountBackups(account, null)
        viewModelScope.launch {
            val found = try {
                repository.backups(feedId, account.id)
            } catch (e: Exception) {
                _events.emit(PublishEvent.Failure(e.toMochiError(), MochiR.string.error_unexpected))
                Backups()
            }
            if (_backups.value?.account?.id == account.id) _backups.value = AccountBackups(account, found.files, found.repository)
        }
    }

    fun closeBackups() {
        _backups.value = null
    }

    /** Where one archive file downloads from, and the token that may fetch it. */
    fun backupFile(account: PublishAccount, name: String): Pair<String, String?> {
        val server = sessionManager.getServerUrlBlocking().trimEnd('/')
        val url = "$server/feeds/$feedId/-/backups/file?account=${encode(account.id)}&name=${encode(name)}"
        return url to sessionManager.getTokenBlocking("feeds")
    }

    /** Add a keyword filter; [onAdded] runs once the server has it. */
    fun addFilter(phrase: String, onAdded: () -> Unit = {}) {
        val value = phrase.trim()
        if (value.isEmpty()) return
        change("filter", R.string.feeds_filter_add_failed) {
            _filters.value = sorted(repository.addFilter(feedId, value.take(100)))
            onAdded()
        }
    }

    fun removeFilter(filter: Filter) {
        change(filter.id, R.string.feeds_filter_remove_failed) {
            _filters.value = sorted(repository.removeFilter(feedId, filter.id))
        }
    }

    // Actions run one at a time, in the order tapped: one tapped while another
    // runs waits here instead of being dropped, and shows as busy meanwhile.
    private val queue = Mutex()

    private fun change(key: String, fallback: Int, block: suspend () -> Unit) {
        // The same action tapped again while it waits or runs is the one tap.
        if (key in _busy.value) return
        _busy.update { it + key }
        viewModelScope.launch {
            queue.withLock {
                try {
                    block()
                } catch (e: Exception) {
                    _events.emit(PublishEvent.Failure(e.toMochiError(), fallback))
                } finally {
                    _busy.update { it - key }
                }
            }
        }
    }

    private fun sorted(filters: List<Filter>) = filters.sortedWith(compareBy(NaturalCompare) { it.phrase })

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
