// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chats marked unread from the list, kept on this device as the web keeps
 * them in its browser: the server has no such mark. Reading the chat, by
 * opening it or by Mark as read, clears it.
 */
@Singleton
class UnreadChatsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _marked =
        MutableStateFlow(prefs.getStringSet(KEY, emptySet())?.toSet() ?: emptySet())
    val marked: StateFlow<Set<String>> = _marked.asStateFlow()

    fun mark(chat: String) = save(_marked.value + chat)

    fun clear(chat: String) {
        if (chat in _marked.value) save(_marked.value - chat)
    }

    private fun save(next: Set<String>) {
        _marked.value = next
        prefs.edit { putStringSet(KEY, next) }
    }

    private companion object {
        const val PREFS = "mochi_chat_unread"
        const val KEY = "marked_chat_ids"
    }
}
