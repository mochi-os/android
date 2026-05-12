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
        if (tag.isNullOrBlank()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)
                ?.applicationLocales = LocaleList.forLanguageTags(tag)
        }
        Locale.setDefault(Locale.forLanguageTag(tag))
    }
}
