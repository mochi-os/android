// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.notifications.MochiNotification
import org.mochios.android.notifications.NotificationCategory
import org.mochios.android.notifications.NotificationTopic
import org.mochios.home.repository.MenuSource
import javax.inject.Inject

/**
 * The user menu's state, as the web menu shows it: who is signed in, and the
 * notifications still unread.
 */
data class HomeMenuState(
    val name: String = "",
    val identity: String = "",
    val unread: List<MochiNotification> = emptyList(),
    val loading: Boolean = true,
    /** The last thing that failed, shown in the menu until the next success. */
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

    /** The unread count the avatar carries, shared with every app's bell. */
    val count: StateFlow<Int> = source.count

    init {
        source.follow()
        viewModelScope.launch {
            runCatching { source.person() }.onSuccess { person ->
                _state.update { it.copy(name = person.name, identity = person.identity) }
            }
        }
    }

    /** Fetch the unread notifications again; called as the menu opens and as the count moves. */
    fun refresh() {
        viewModelScope.launch {
            try {
                val list = source.unread()
                _state.update { it.copy(unread = list, loading = false, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.toMochiError()) }
            }
        }
    }

    /** A notification tapped: it is read now, so it leaves the list. */
    fun read(notification: MochiNotification) {
        if (!notification.isUnread) return
        _state.update { state -> state.copy(unread = state.unread.filterNot { it.id == notification.id }) }
        viewModelScope.launch {
            try {
                source.read(notification.id)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.toMochiError()) }
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
            } catch (e: Exception) {
                _state.update { it.copy(unread = before, error = e.toMochiError()) }
            }
            source.recount()
        }
    }

    /** Open [notification]'s category picker, loading the categories and its topic. */
    fun pick(notification: MochiNotification) {
        _state.update { it.copy(picker = CategoryPicker(notification.id)) }
        viewModelScope.launch {
            val picker = try {
                val categories = async { source.categories() }
                val topic = async { source.topic(notification) }
                CategoryPicker(notification.id, categories.await(), topic.await())
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
            } catch (e: Exception) {
                _state.update { it.copy(error = e.toMochiError()) }
            }
        }
    }
}
