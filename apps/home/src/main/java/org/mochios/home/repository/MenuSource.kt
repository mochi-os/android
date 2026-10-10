// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.home.repository

import kotlinx.coroutines.flow.StateFlow
import org.mochios.android.auth.AuthRepository
import org.mochios.android.notifications.MochiNotification
import org.mochios.android.notifications.NotificationCategory
import org.mochios.android.notifications.NotificationTopic
import org.mochios.android.notifications.NotificationsRepository
import org.mochios.android.notifications.NotificationsUnreadStore
import javax.inject.Inject

/** Who is signed in, as the user menu shows them. */
data class Person(val identity: String, val name: String)

/** Everything the user menu reads from and changes on the server. */
interface MenuSource {
    /** The unread count every app's bell shows. */
    val count: StateFlow<Int>

    /** Start following the count, if nothing has yet. */
    fun follow()

    /** Fetch the count again, after the menu has changed it. */
    suspend fun recount()

    suspend fun person(): Person
    suspend fun unread(): List<MochiNotification>
    suspend fun read(id: String)
    suspend fun readAll()
    suspend fun categories(): List<NotificationCategory>
    suspend fun topic(notification: MochiNotification): NotificationTopic?
    suspend fun categorise(notification: MochiNotification, category: String?)
}

/** The menu's source on the server: the identity endpoint and the notifications app. */
class ServerMenuSource @Inject constructor(
    private val notifications: NotificationsRepository,
    private val store: NotificationsUnreadStore,
    private val auth: AuthRepository,
) : MenuSource {
    override val count: StateFlow<Int> = store.count

    override fun follow() = store.ensureStarted()

    override suspend fun recount() = store.refresh()

    override suspend fun person(): Person =
        auth.getIdentityInfo().identity.let { Person(it.identity, it.name) }

    override suspend fun unread(): List<MochiNotification> =
        notifications.list().data.filter { it.isUnread }

    override suspend fun read(id: String) = notifications.markRead(id)

    override suspend fun readAll() = notifications.markAllRead()

    override suspend fun categories(): List<NotificationCategory> = notifications.categories()

    override suspend fun topic(notification: MochiNotification): NotificationTopic? =
        notifications.topic(notification)

    override suspend fun categorise(notification: MochiNotification, category: String?) =
        notifications.setCategory(notification, category)
}
