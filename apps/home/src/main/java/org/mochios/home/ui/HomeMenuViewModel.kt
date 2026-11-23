// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.messageOrFallback
import org.mochios.android.api.toMochiError
import org.mochios.android.i18n.AppContext
import org.mochios.android.notifications.MochiNotification
import org.mochios.android.notifications.NotificationCategory
import org.mochios.android.notifications.NotificationTopic
import org.mochios.home.R
import org.mochios.home.repository.MenuSource
import javax.inject.Inject

/**
 * The user menu's state, as the web menu shows it: who is signed in, and the
 * notifications still unread.
 */
data class HomeMenuState(
    val name: String = "",
    val identity: String = "",
    /** The accent colour the user's avatar is ringed in, as on the web; null for none. */
    val accent: String? = null,
    val unread: List<MochiNotification> = emptyList(),
    val loading: Boolean = true,
    /** Why the list could not be read, shown in the menu until it is read. */
    val error: MochiError? = null,
    val picker: CategoryPicker? = null,
)

/** The category picker open on one notification's row. */
data class CategoryPicker(
    val notification: String,
    /** Null while loading. */
    val categories: List<NotificationCategory>? = null,
    /** The row's topic; null when the server holds none for it yet. */
    val topic: NotificationTopic? = null,
    val error: MochiError? = null,
)

@HiltViewModel
class HomeMenuViewModel @Inject constructor(
    private val source: MenuSource,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeMenuState())
    val state: StateFlow<HomeMenuState> = _state.asStateFlow()

    /**
     * Why a read, a read of them all or a move to a category failed, each said
     * once wherever the user is by then: the menu closes on some of them, and
     * the list read after them must not wipe what went wrong. Held until the
     * home screen is there to show it.
     */
    private val _failures = Channel<String>(Channel.BUFFERED)
    val failures: Flow<String> = _failures.receiveAsFlow()

    /** The unread count the avatar carries, shared with every app's bell. */
    val count: StateFlow<Int> = source.count

    // Whether the server has said who is signed in: until it has, each return
    // to the home screen asks again.
    private var known = false

    init {
        source.follow()
        viewModelScope.launch {
            // Who the device holds draws the avatar at once, offline too, until
            // the server answers.
            runCatching { source.held() }.getOrNull()?.let { person ->
                _state.update { it.copy(name = person.name, identity = person.identity) }
            }
            identify()
        }
    }

    /**
     * Read the user's accent again, and who they are while the server has not
     * yet said; called as the home screen comes back, so one changed
     * elsewhere rings the avatar and a start without the network is made good
     * once it returns. A failed read keeps what was shown.
     */
    fun restyle() {
        viewModelScope.launch { if (known) accent() else identify() }
    }

    /** Who is signed in, from the server, then their accent. */
    private suspend fun identify() {
        runCatching { source.person() }.onSuccess { person ->
            known = true
            _state.update { it.copy(name = person.name, identity = person.identity) }
        }
        accent()
    }

    private suspend fun accent() {
        val identity = _state.value.identity
        if (identity.isBlank()) return
        runCatching { source.accent(identity) }.onSuccess { accent ->
            _state.update { it.copy(accent = accent) }
        }
    }

    /** Fetch the unread notifications again; called as the menu opens and as the count moves. */
    fun refresh() {
        viewModelScope.launch {
            try {
                val list = source.unread()
                _state.update { it.copy(unread = list, loading = false, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.toMochiError()) }
            }
        }
    }

    /** A notification tapped: it is read now, so it leaves the list, and comes back if the server refuses. */
    fun read(notification: MochiNotification) {
        if (!notification.isUnread) return
        _state.update { state -> state.copy(unread = state.unread.filterNot { it.id == notification.id }) }
        viewModelScope.launch {
            try {
                source.read(notification.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _failures.send(failure(e, R.string.home_menu_read_failed))
                refresh()
            }
            source.recount()
        }
    }

    fun readAll() {
        val before = _state.value.unread
        _state.update { it.copy(unread = emptyList()) }
        viewModelScope.launch {
            try {
                source.readAll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(unread = before) }
                _failures.send(failure(e, R.string.home_menu_read_all_failed))
            }
            source.recount()
        }
    }

    /** The server's reason for a refusal, else the action's own words for its failure. */
    private fun failure(e: Throwable, fallback: Int): String =
        e.toMochiError().messageOrFallback(AppContext.get().getString(fallback))

    /** Open [notification]'s category picker, loading the categories and its topic. */
    fun pick(notification: MochiNotification) {
        _state.update { it.copy(picker = CategoryPicker(notification.id)) }
        viewModelScope.launch {
            val picker = try {
                val categories = async { source.categories() }
                val topic = async { source.topic(notification) }
                CategoryPicker(notification.id, categories.await(), topic.await())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CategoryPicker(notification.id, emptyList(), null, e.toMochiError())
            }
            // Another row's picker may have opened meanwhile; this answer is then stale.
            _state.update { state -> if (state.picker?.notification == notification.id) state.copy(picker = picker) else state }
        }
    }

    fun closePicker() {
        _state.update { it.copy(picker = null) }
    }

    /** Move [notification]'s topic to [category], or back to unassigned when null. */
    fun categorise(notification: MochiNotification, category: String?) {
        _state.update { it.copy(picker = null) }
        viewModelScope.launch {
            try {
                source.categorise(notification, category)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _failures.send(failure(e, R.string.home_menu_category_failed))
            }
        }
    }
}
