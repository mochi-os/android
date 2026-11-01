// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.websocket

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.mochios.android.auth.AuthRepository
import org.mochios.android.auth.SessionManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The server and the per-app JWTs [MochiWebSocket] authenticates its handshakes
 * with, behind an interface so the socket stays constructible without a
 * [android.content.Context].
 */
interface SocketSession {

    /** Base URL of the server the bound account belongs to, no trailing slash. */
    suspend fun serverUrl(): String

    /** The cached JWT for [app], minted on first use; null when unreachable. */
    suspend fun token(app: String): String?

    /** Forget [app]'s cached JWT so the next [token] mints a fresh one. */
    suspend fun invalidate(app: String)

    /**
     * The signed-in account's identity, or null when nobody is signed in: the
     * user the session's own sockets count against under core's cap.
     */
    fun identity(): String? = null
}

/** [SocketSession] backed by the signed-in session. */
@Singleton
class BoundSocketSession @Inject constructor(
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository,
) : SocketSession {

    override suspend fun serverUrl(): String = sessionManager.serverUrl.first().trimEnd('/')

    override suspend fun token(app: String): String? =
        sessionManager.getToken(app) ?: authRepository.fetchToken(app).getOrNull()

    override suspend fun invalidate(app: String) {
        sessionManager.clearToken(app)
    }

    override fun identity(): String? = runBlocking { sessionManager.getBoundIdentity() }
}

/** Binds [BoundSocketSession] as the app's [SocketSession]. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SocketSessionModule {

    /** @return the session-backed implementation. */
    @Binds
    @Singleton
    abstract fun bindSocketSession(impl: BoundSocketSession): SocketSession
}
