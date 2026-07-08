// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

/**
 * Local-only record of which chats the user has pinned. The server has no pin
 * concept, so pins live entirely on this device: they persist across restarts
 * via SharedPreferences and surface reactively through [pinned].
 *
 * Chats are keyed by their navigation id (`fingerprint`, or `id` when the
 * fingerprint is blank) so the same key resolves from both the conversation
 * screen and the drawer chat list.
 */
@Singleton
class PinnedChatsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _pinned =
        MutableStateFlow(prefs.getStringSet(KEY, emptySet())?.toSet() ?: emptySet())
    val pinned: StateFlow<Set<String>> = _pinned.asStateFlow()

    /** True when [chatKey] is currently pinned. */
    fun isPinned(chatKey: String): Boolean = chatKey in _pinned.value

    /** Flip the pin state for [chatKey] and persist the new set. */
    fun toggle(chatKey: String) {
        if (chatKey.isBlank()) return
        val next = _pinned.value.toMutableSet()
        if (!next.add(chatKey)) {
            next.remove(chatKey)
        }
        _pinned.value = next
        prefs.edit { putStringSet(KEY, next) }
    }

    private companion object {
        const val PREFS = "mochi_chat_pins"
        const val KEY = "pinned_chat_ids"
    }
}
