package org.mochios.mochi

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp
import org.mochios.android.i18n.AppContext
import org.mochios.android.i18n.LanguageStore
import org.mochios.android.i18n.LocaleHelper
import org.mochios.android.push.PushServiceWatchdog
import org.mochios.android.update.UpdateChecker
import org.mochios.chat.notifications.setupChatNotificationChannel
import org.mochios.feeds.notifications.setupFeedsNotificationChannel
import org.mochios.forums.notifications.setupForumsNotificationChannel
import org.mochios.projects.notifications.setupProjectsNotificationChannel

@HiltAndroidApp
class MochiApplication : Application() {

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
        PushServiceWatchdog.schedule(this)
        UpdateChecker.schedule(this)
    }
}
