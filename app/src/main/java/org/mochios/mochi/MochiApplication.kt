package org.mochios.mochi

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import org.mochios.android.api.AssetHttpClient
import org.mochios.android.auth.SessionManager
import org.mochios.android.i18n.AppContext
import org.mochios.android.ui.components.VideoFrameFetcher
import org.mochios.android.i18n.LanguageStore
import org.mochios.android.i18n.LocaleHelper
import org.mochios.android.push.PushServiceWatchdog
import org.mochios.android.update.UpdateChecker
import org.mochios.chat.notifications.setupChatNotificationChannel
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

    /**
     * Authenticate Coil image requests the same way the API clients do. Avatars
     * and chat/feed/etc. attachments are served from per-app, session-gated
     * routes; the default Coil loader sends neither the session cookie nor the
     * per-app bearer token, so those images come back 401 and render blank. The
     * shared [AssetHttpClient] adds both (see `AssetHttpModule`).
     *
     * [VideoFrameFetcher] adds cached video poster frames (range-extracted via
     * MediaMetadataRetriever) for [VideoFrame] requests.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { assetHttpClient }))
                add(VideoFrameFetcher.Factory(sessionManager))
            }
            .build()

    /**
     * Application-scoped coroutine scope. Used for long-lived observers that
     * outlive any single Activity but should end when the process ends. A
     * fresh SupervisorJob means a crash in one collector doesn't cascade to
     * the others. Default dispatcher matches what
     * [StaffAccessController.start] needs (background work + a `me()` round
     * trip).
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base, LanguageStore.get(base)))
    }

    override fun onCreate() {
        super.onCreate()
        AppContext.set(this)
        LocaleHelper.apply(this, LanguageStore.get(this))
        setupFeedsNotificationChannel(this)
        setupChatNotificationChannel(this)
        setupForumsNotificationChannel(this)
        setupProjectsNotificationChannel(this)
        setupPeopleNotificationChannel(this)
        setupWikisNotificationChannel(this)
        setupChessNotificationChannel(this)
        setupGoNotificationChannel(this)
        setupWordsNotificationChannel(this)
        setupMarketNotificationChannel(this)
        setupStaffNotificationChannel(this)
        PushServiceWatchdog.schedule(this)
        UpdateChecker.schedule(this)
        // Start watching the bound identity so the Mochi Staff launcher
        // alias appears the moment a staff user signs in and disappears
        // again on sign-out / role revocation. The applicationScope
        // outlives any single Activity, so the observation survives
        // configuration changes and Activity teardown.
        staffAccessController.start(applicationScope)
    }
}
