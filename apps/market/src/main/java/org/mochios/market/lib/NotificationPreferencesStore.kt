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

/**
 * Backing DataStore for the [NotificationPreferencesStore]. Kept in its
 * own file so app data wipes hit it independently of the saved /
 * recently-viewed / reported stores.
 */
private val Context.notificationPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "market_notification_topics",
)

/**
 * Local-only store for per-topic mute preferences on the market app.
 *
 * Notification topic mute is a per-user setting that lives on the
 * server's notifications app, but that app's APIs aren't reachable
 * from the market client (Mochi forbids cross-app HTTP requests
 * inside an app and the Comptroller doesn't proxy these specific
 * routes). Until a market-side mute API ships, the screen surfaces
 * every topic the user can hit so they at least see what exists and
 * can record their preference locally — the values are then applied
 * client-side by filtering inbound notifications by topic.
 *
 * The set stores the **muted** topic keys (e.g. `"order.seller"`).
 * Topics whose key is absent are treated as enabled, which means a
 * fresh install starts with everything on, matching server defaults.
 *
 * Topic keys are the same dotted-component strings declared in
 * `apps/market/labels/en.conf` under `notifications.topic.*` — see
 * [MarketNotificationTopics] for the canonical list the UI walks.
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
 * Canonical ordered list of every market notification topic the user
 * can mute. The order matches the `notifications.topic.*` block in
 * `apps/market/labels/en.conf` so the on-screen list looks the same
 * as the per-locale label catalogs.
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
