package org.mochios.mochi

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.mochios.android.i18n.AppContext
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
class MochiApplication : Application() {

    @Inject lateinit var staffAccessController: StaffAccessController

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
