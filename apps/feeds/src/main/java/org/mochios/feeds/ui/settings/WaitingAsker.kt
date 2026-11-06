// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.
package org.mochios.feeds.ui.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.feeds.model.Imported
import org.mochios.feeds.repository.FeedsRepository
import org.mochios.feeds.repository.PermissionRequiredException

/**
 * The servers people of a feed's imports wait on, asked for one at a time:
 * each is tried, the permission it is refused for is put to the user as
 * [pending], and allowing it tries again. [added] hears what each allowed
 * server brought, [failed] what went wrong.
 */
internal class WaitingAsker(
    private val scope: CoroutineScope,
    private val repository: FeedsRepository,
    private val feedId: String,
    private val added: (Imported) -> Unit,
    private val failed: (Exception) -> Unit,
) {
    private val queue = ArrayDeque<String>()
    private var running = false

    private val _pending = MutableStateFlow<PendingPermission?>(null)
    val pending: StateFlow<PendingPermission?> = _pending.asStateFlow()

    // Whether a server is being granted or its people added, for a screen to
    // show; not while the user is being asked, which shows itself.
    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working.asStateFlow()

    /** Ask for each of [servers], after any already being asked for. */
    fun ask(servers: List<String>) {
        servers.filterTo(queue) { server -> server !in queue }
        if (!running) next()
    }

    /** The user allows the server [pending] names: grant it, then add its people. */
    fun allow() {
        val pending = _pending.value ?: return
        val server = pending.retryServer ?: return
        _pending.value = null
        _working.value = true
        scope.launch {
            try {
                repository.grantPermission(pending.app, pending.permission)
                attempt(server)
            } catch (e: Exception) {
                failed(e)
                next()
            }
        }
    }

    /** The user declines: its people wait on, and the next server is asked for. */
    fun decline() {
        _pending.value = null
        next()
    }

    private fun next() {
        val server = queue.removeFirstOrNull()
        running = server != null
        _working.value = server != null
        if (server != null) scope.launch { attempt(server) }
    }

    private suspend fun attempt(server: String) {
        try {
            added(repository.allowWaiting(feedId, server))
            next()
        } catch (e: PermissionRequiredException) {
            _working.value = false
            val name = try {
                repository.permissionName(e.permission)
            } catch (_: Exception) {
                e.permission
            }
            _pending.value = PendingPermission(app = e.app, permission = e.permission, name = name, retryUrl = "", retryType = "", retryServer = server)
        } catch (e: Exception) {
            failed(e)
            next()
        }
    }
}
