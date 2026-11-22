// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.notifications

import kotlinx.coroutines.CancellationException
import org.mochios.android.api.toMochiError
import org.mochios.android.api.unwrap
import org.mochios.android.api.unwrapEmpty
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The notifications app's calls. Each throws a [org.mochios.android.api.MochiError]
 * carrying the server's own refusal, so a screen shows the server's reason
 * rather than a bare status.
 */
@Singleton
class NotificationsRepository @Inject constructor(
    private val api: NotificationsApi,
) {

    private inline fun <T> request(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw e.toMochiError()
    }

    // An empty body on a success is a valid answer for these four (no rows,
    // no count, no topic yet), so only a refusal throws.

    suspend fun list(): NotificationsListResponse = request {
        val resp = api.list()
        resp.unwrapEmpty()
        resp.body() ?: NotificationsListResponse()
    }

    suspend fun count(): NotificationsCount = request {
        val resp = api.count()
        resp.unwrapEmpty()
        resp.body()?.data ?: NotificationsCount()
    }

    suspend fun markRead(id: String) = request {
        api.read(id).unwrapEmpty()
    }

    suspend fun markAllRead() = request {
        api.readAll().unwrapEmpty()
    }

    suspend fun categories(): List<NotificationCategory> = request {
        val resp = api.categories()
        resp.unwrapEmpty()
        resp.body()?.data ?: emptyList()
    }

    /** The topic [notification] belongs to, or null when the server holds no row for it yet. */
    suspend fun topic(notification: MochiNotification): NotificationTopic? = request {
        val resp = api.topic(notification.app, notification.topic, notification.`object`)
        resp.unwrapEmpty()
        resp.body()?.data
    }

    /** Moves [notification]'s topic to [category], or back to unassigned when null. */
    suspend fun setCategory(notification: MochiNotification, category: String?) = request {
        api.setCategory(notification.app, notification.topic, notification.`object`, category.orEmpty()).unwrapEmpty()
    }

    suspend fun clearAll() = request {
        api.clearAll().unwrapEmpty()
    }

    /**
     * Remove the push account [id] the server minted for this device. Call
     * while the session is still valid - the request needs the notifications
     * app token.
     */
    suspend fun removeAccount(id: String) = request {
        api.removeAccount(id).unwrapEmpty()
    }

    // RSS feeds of the notifications. Each throws the server's own refusal.

    suspend fun rssFeeds(): List<RssFeed> = api.rssFeeds().unwrap()

    suspend fun createRssFeed(name: String, existing: Boolean): RssFeed =
        api.createRssFeed(name, if (existing) "1" else "0").unwrap()

    suspend fun renameRssFeed(id: String, name: String) {
        api.renameRssFeed(id, name).unwrap()
    }

    suspend fun enableRssFeed(id: String, enabled: Boolean) {
        api.enableRssFeed(id, if (enabled) "1" else "0").unwrap()
    }

    suspend fun deleteRssFeed(id: String) {
        api.deleteRssFeed(id).unwrap()
    }
}
