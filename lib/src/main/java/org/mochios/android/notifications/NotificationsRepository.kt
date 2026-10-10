// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.notifications

import org.mochios.android.api.toMochiError
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationsRepository @Inject constructor(
    private val api: NotificationsApi,
) {

    suspend fun list(): NotificationsListResponse {
        try {
            val resp = api.list()
            if (resp.isSuccessful) return resp.body() ?: NotificationsListResponse()
            throw RuntimeException("HTTP ${resp.code()}")
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun count(): NotificationsCount {
        try {
            val resp = api.count()
            if (resp.isSuccessful) return resp.body()?.data ?: NotificationsCount()
            throw RuntimeException("HTTP ${resp.code()}")
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun markRead(id: String) {
        try {
            val resp = api.read(id)
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code()}")
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun markAllRead() {
        try {
            val resp = api.readAll()
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code()}")
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun categories(): List<NotificationCategory> {
        try {
            val resp = api.categories()
            if (resp.isSuccessful) return resp.body()?.data ?: emptyList()
            throw RuntimeException("HTTP ${resp.code()}")
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    /** The topic [notification] belongs to, or null when the server holds no row for it yet. */
    suspend fun topic(notification: MochiNotification): NotificationTopic? {
        try {
            val resp = api.topic(notification.app, notification.topic, notification.`object`)
            if (resp.isSuccessful) return resp.body()?.data
            throw RuntimeException("HTTP ${resp.code()}")
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    /** Moves [notification]'s topic to [category], or back to unassigned when null. */
    suspend fun setCategory(notification: MochiNotification, category: String?) {
        try {
            val resp = api.setCategory(notification.app, notification.topic, notification.`object`, category.orEmpty())
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code()}")
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun clearAll() {
        try {
            val resp = api.clearAll()
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code()}")
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    /**
     * Remove the push account [id] the server minted for this device. Call
     * while the session is still valid - the request needs the notifications
     * app token.
     */
    suspend fun removeAccount(id: String) {
        try {
            val resp = api.removeAccount(id)
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code()}")
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }
}
