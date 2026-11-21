// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import org.mochios.android.auth.SessionManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chats marked unread from the list, kept on this device for the account
 * signed in, as the web keeps them in its browser: the server has no such
 * mark. Reading the chat, by opening it or by Mark as read, clears it.
 */
@Singleton
class UnreadChatsStore @Inject constructor(
    @ApplicationContext context: Context,
    session: SessionManager,
) {
    private val chats = AccountChats(context, PREFS, LEGACY, session.getBoundIdentityBlocking(), session.boundIdentity)

    val marked: StateFlow<Set<String>> = chats.ids

    fun mark(chat: String) = chats.change { marks -> marks + chat }

    fun clear(chat: String) = chats.change { marks -> marks - chat }

    /** Drop the marks of chats the account's whole list, [listed], no longer has. */
    fun prune(listed: Set<String>) = chats.prune(listed)

    private companion object {
        const val PREFS = "mochi_chat_unread"
        const val LEGACY = "marked_chat_ids"
    }
}
