// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.mochi

import android.app.Application
import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.network.DeDupeConcurrentRequestStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import org.mochios.android.api.AssetHttpClient
import org.mochios.android.auth.SessionManager
import org.mochios.android.i18n.AppContext
import org.mochios.android.ui.components.OwnedInterceptor
import org.mochios.android.ui.components.RelativeAssetUrlMapper
import org.mochios.android.ui.components.RevalidatingCacheStrategy
import org.mochios.android.ui.components.StaleIfError
import org.mochios.android.ui.components.VideoFrameFetcher
import org.mochios.android.i18n.LanguageStore
import org.mochios.android.i18n.LocaleHelper
import org.mochios.android.i18n.PreferencesManager
import org.mochios.android.push.PushServiceWatchdog
import org.mochios.android.update.UpdateChecker
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.calendars.notifications.setupCalendarsNotificationChannel
import org.mochios.chat.notifications.setupChatNotificationChannel
import org.mochios.crm.notifications.setupCrmsNotificationChannel
import org.mochios.feeds.notifications.setupFeedsNotificationChannel
import org.mochios.forums.notifications.setupForumsNotificationChannel
import org.mochios.people.notifications.setupPeopleNotificationChannel
import org.mochios.projects.notifications.setupProjectsNotificationChannel
import org.mochios.wikis.notifications.setupWikisNotificationChannel
import org.mochios.chess.notifications.setupChessNotificationChannel
import org.mochios.go.notifications.setupGoNotificationChannel
import org.mochios.words.notifications.setupWordsNotificationChannel
import org.mochios.market.notifications.setupMarketNotificationChannel
import org.mochios.staff.access.StaffAccessController
import org.mochios.staff.notifications.setupStaffNotificationChannel
import javax.inject.Inject

@HiltAndroidApp
class MochiApplication : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var staffAccessController: StaffAccessController

    @Inject @AssetHttpClient lateinit var assetHttpClient: OkHttpClient

    @Inject lateinit var sessionManager: SessionManager

    @Inject lateinit var preferencesManager: PreferencesManager

    @Inject lateinit var webSocket: MochiWebSocket

    /**
     * Coil loader authenticated like the API clients: attachment and avatar
     * routes are session-gated, and the default loader sends neither the cookie
     * nor the bearer token, so images 401.
     */
    @OptIn(ExperimentalCoilApi::class)
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                // Mark a request for one of the app's own, server-relative
                // addresses, the only kind the asset client sends the session
                // and a token with: an absolute URL may be a peer's choice.
                add(OwnedInterceptor())
                // Expand server-relative asset paths ("/people/<id>/-/avatar")
                // to absolute URLs, so call sites pass relative paths without
                // threading the server URL through.
                add(RelativeAssetUrlMapper(sessionManager))
                // Avatars live at addresses that do not change with them; the
                // strategy asks again once the server's max-age has passed, and
                // a server that fails the asking leaves the held image shown.
                // One load of an address at a time: loads asking together each
                // held the cached copy open, so none could store the answer.
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { assetHttpClient.newBuilder().addInterceptor(StaleIfError()).build() },
                        cacheStrategy = { RevalidatingCacheStrategy() },
                        concurrentRequestStrategy = { DeDupeConcurrentRequestStrategy() },
                    )
                )
                add(VideoFrameFetcher.Factory(sessionManager) { assetHttpClient })
            }
            .build()

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base, LanguageStore.get(base)))
    }

    override fun onCreate() {
        super.onCreate()
        AppContext.set(this)
        setupFeedsNotificationChannel(this)
        setupChatNotificationChannel(this)
        setupForumsNotificationChannel(this)
        setupProjectsNotificationChannel(this)
        setupCrmsNotificationChannel(this)
        setupPeopleNotificationChannel(this)
        setupWikisNotificationChannel(this)
        setupChessNotificationChannel(this)
        setupGoNotificationChannel(this)
        setupWordsNotificationChannel(this)
        setupMarketNotificationChannel(this)
        setupStaffNotificationChannel(this)
        setupCalendarsNotificationChannel(this)
        PushServiceWatchdog.schedule(this)
        UpdateChecker.schedule(this)
        // Shows and hides the Mochi Staff launcher alias as the bound
        // identity's staff role changes.
        staffAccessController.start(applicationScope)
        // Reads the account's preferences again each time the app comes back
        // to the foreground, so a change made on the web applies.
        preferencesManager.watch(ProcessLifecycleOwner.get().lifecycle, applicationScope)
        // Closes the screens' websockets while the app is in the background,
        // and opens them again as it returns.
        webSocket.watch(ProcessLifecycleOwner.get().lifecycle)
    }
}
