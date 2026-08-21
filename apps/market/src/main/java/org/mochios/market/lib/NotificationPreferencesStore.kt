// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.lib

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.notificationPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "market_notification_topics",
)

/**
 * Local-only per-topic mute preferences: the notifications app is not reachable
 * from the market client, so muting is applied client-side by filtering inbound
 * notifications. Stores the muted topic keys (absent = enabled), matching
 * `notifications.topic.*` in `apps/market/labels/en.conf`.
 */
@Singleton
class NotificationPreferencesStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val dataStore = context.notificationPreferencesDataStore

    fun observe(): Flow<Set<String>> =
        dataStore.data.map { prefs -> prefs[KEY_MUTED] ?: emptySet() }

    suspend fun current(): Set<String> {
        return dataStore.data.first()[KEY_MUTED] ?: emptySet()
    }

    suspend fun isEnabled(topic: String): Boolean {
        if (topic.isBlank()) return true
        return topic !in current()
    }

    /**
     * Mute or unmute a topic. The DataStore writes atomically so we
     * never end up with the same key listed twice.
     */
    suspend fun setEnabled(topic: String, enabled: Boolean) {
        if (topic.isBlank()) return
        dataStore.edit { prefs ->
            val current = prefs[KEY_MUTED] ?: emptySet()
            prefs[KEY_MUTED] = if (enabled) current - topic else current + topic
        }
    }

    companion object {
        private val KEY_MUTED = stringSetPreferencesKey("muted_topics")
    }
}

/**
 * Every mutable topic, in the order of the `notifications.topic.*` block in
 * `apps/market/labels/en.conf`.
 */
object MarketNotificationTopics {
    /** Every topic key in the order the UI should render them. */
    val ALL: List<String> = listOf(
        "message",
        "order.seller",
        "order.buyer",
        "bid.placed",
        "auction.outbid",
        "auction.ended",
        "auction.cancelled",
        "subscription.seller",
        "subscription.buyer",
        "listing.moderation",
        "review.received",
        "review.responded",
        "report.reporter",
        "report.target",
        "account.moderation",
        "account.stripe",
    )
}
