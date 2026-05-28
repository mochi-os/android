package org.mochios.android.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {

    fun wrap(context: Context, tag: String?): Context {
        if (tag.isNullOrBlank()) return context
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        return context.createConfigurationContext(config)
    }

    fun apply(context: Context, tag: String?) {
        // A blank tag means "follow the device locale": clear the per-app
        // override (TIRAMISU+) so the system reverts, and leave the JVM
        // default untouched — the next process launch re-derives it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag.isNullOrBlank()) LocaleList.getEmptyLocaleList()
                else LocaleList.forLanguageTags(tag)
        }
        if (!tag.isNullOrBlank()) Locale.setDefault(Locale.forLanguageTag(tag))
    }
}
