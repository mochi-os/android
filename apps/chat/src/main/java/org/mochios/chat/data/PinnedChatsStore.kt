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
 * The chats pinned to the top of the list, on this device and for the account
 * signed in (the server has no pin concept), by chat id so the list and the
 * conversation agree.
 */
@Singleton
class PinnedChatsStore @Inject constructor(
    @ApplicationContext context: Context,
    session: SessionManager,
) {
    private val chats = AccountChats(context, PREFS, LEGACY, session.getBoundIdentityBlocking(), session.boundIdentity)

    val pinned: StateFlow<Set<String>> = chats.ids

    /** Flip the pin state for [chat]. */
    fun toggle(chat: String) {
        if (chat.isBlank()) return
        chats.change { pins -> if (chat in pins) pins - chat else pins + chat }
    }

    /** Drop the pins of chats [account]'s whole list, [listed], no longer has, while it is signed in. */
    fun prune(listed: Set<String>, account: String?) = chats.prune(listed, account)

    private companion object {
        const val PREFS = "mochi_chat_pins"
        const val LEGACY = "pinned_chat_ids"
    }
}
