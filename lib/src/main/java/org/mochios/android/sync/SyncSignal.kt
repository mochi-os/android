// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.sync

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.mochios.android.auth.SessionManager

/**
 * A push carrying only {"sync": kind}: the server's word that the user's
 * calendars or contacts changed. It runs that sync now, where this phone syncs
 * it, instead of waiting for the next scheduled one; it posts nothing.
 */
object SyncSignal {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The sync a push asks for, or null for an ordinary notification. */
    fun kind(fields: Map<String, String>): String? = fields["sync"]?.takeIf { it.isNotBlank() }

    fun run(context: Context, kind: String, session: SessionManager) {
        val app = context.applicationContext
        scope.launch {
            val identity = session.getBoundIdentity()
            when (kind) {
                "calendars" -> CalendarsSync.account(app, identity)?.let {
                    if (CalendarsSync.enabled(it)) CalendarsSync.request(it)
                }
                "contacts" -> ContactsSync.account(app, identity)?.let {
                    if (ContactsSync.enabled(it)) ContactsSync.request(it)
                }
            }
        }
    }
}
