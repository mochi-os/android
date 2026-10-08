// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.launcher

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.mochios.android.push.LAUNCHER_ACTIVITIES
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A colour a launcher icon comes in: one per theme the server offers, named
 * by the [suffix] its manifest alias carries, at that theme's OKLCH [hue] and
 * [chroma].
 */
enum class IconTint(val suffix: String, val hue: Float, val chroma: Float) {
    BLUE("Blue", 250f, 0.135f),
    ROSE("Rose", 350f, 0.135f),
    STEEL("Steel", 250f, 0.055f),
    TEAL("Teal", 185f, 0.09f),
    TERRACOTTA("Terracotta", 40f, 0.15f),
}

/**
 * Colours the launcher icons to the user's theme. Android cannot recolour an
 * icon at run time, so each app has one manifest alias per [IconTint], blue
 * enabled out of the box, and this enables the one nearest the theme and
 * disables the rest. A theme the app does not know gets the nearest colour it
 * has. An app whose launcher activity is switched off, as Staff is for a user
 * without staff access, shows no alias at all.
 *
 * Each app runs under the alias it was opened through, and Android closes an
 * activity whose alias is disabled. So a new colour waits until no Mochi
 * activity is started, as [watch] sees, and the app being used is never closed
 * under the user. A launcher drops a disabled alias's icon from the home
 * screen, and Android closes the Mochi tasks left running under it, so a change
 * of colour leaves the apps in the app drawer and out of Recents.
 */
object LauncherTint {

    private const val TAG = "LauncherTint"

    private const val STORE = "launcher_tint"

    /** The tint the theme asks for. */
    private const val KEY_WANTED = "tint"

    /** The tint the launcher shows now, behind [KEY_WANTED] until the switch. */
    private const val KEY_SHOWN = "shown"

    /** How long Mochi stays out of sight before the icons change colour, in ms. */
    private const val SETTLE = 1_000L

    private val handler by lazy { Handler(Looper.getMainLooper()) }

    private var started = 0

    /**
     * The tint nearest a theme's OKLCH [hue] and [chroma], measured on the
     * colour wheel itself, so a muted theme and a vivid one of the same hue
     * stay apart.
     */
    fun tintFor(hue: Float, chroma: Float): IconTint =
        IconTint.entries.minBy { tint -> distance(hue, chroma, tint.hue, tint.chroma) }

    /** Asks for the tint nearest the theme, shown once Mochi is out of sight. */
    fun apply(context: Context, hue: Float, chroma: Float) {
        val tint = tintFor(hue, chroma)
        Log.d(TAG, "Theme hue $hue chroma $chroma is ${tint.suffix}")
        want(context, tint)
    }

    /**
     * Asks for blue, the colour of the default theme, for when the theme is
     * forgotten, as it is on logout.
     */
    fun reset(context: Context) {
        want(context, IconTint.BLUE)
    }

    /**
     * Counts the Mochi activities started in [application], and shows the
     * tint asked for once none has been for [SETTLE].
     */
    fun watch(application: Application) {
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                started++
            }

            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                if (started == 0) {
                    handler.postDelayed({ settle(application) }, SETTLE)
                }
            }

            override fun onActivityCreated(activity: Activity, state: Bundle?) {
            }

            override fun onActivityResumed(activity: Activity) {
            }

            override fun onActivityPaused(activity: Activity) {
            }

            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {
            }

            override fun onActivityDestroyed(activity: Activity) {
            }
        }
        application.registerActivityLifecycleCallbacks(callbacks)
    }

    /**
     * Shows every app's icon in the tint shown now, for when a launcher
     * activity has just been switched on or off; a tint still waiting stays
     * waiting.
     */
    fun refresh(context: Context) {
        show(context, read(context, KEY_SHOWN))
    }

    private fun want(context: Context, tint: IconTint) {
        store(context).edit().putString(KEY_WANTED, tint.name).apply()
        if (started == 0) {
            settle(context)
        }
    }

    private fun settle(context: Context) {
        if (started > 0) {
            return
        }
        val wanted = read(context, KEY_WANTED)
        if (wanted == read(context, KEY_SHOWN)) {
            return
        }
        show(context, wanted)
        store(context).edit().putString(KEY_SHOWN, wanted.name).apply()
    }

    private fun store(context: Context) = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    private fun read(context: Context, key: String): IconTint {
        val saved = store(context).getString(key, null)
        return IconTint.entries.firstOrNull { entry -> entry.name == saved } ?: IconTint.BLUE
    }

    private fun show(context: Context, tint: IconTint) {
        val pm = context.packageManager
        for (launcher in LAUNCHER_ACTIVITIES.values) {
            val target = ComponentName(context, "${context.packageName}.$launcher")
            val visible = enabled(pm, target)
            for (each in IconTint.entries) {
                val component = alias(context, launcher, each)
                val wanted = visible && each == tint
                if (enabled(pm, component) != wanted) {
                    if (wanted) {
                        Log.i(TAG, "Launcher icon $launcher now ${tint.suffix}")
                    }
                    pm.setComponentEnabledSetting(
                        component,
                        if (wanted) {
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        } else {
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        },
                        PackageManager.DONT_KILL_APP,
                    )
                }
            }
        }
    }

    /**
     * The launcher entry [launcher] shows now, its enabled alias, so a
     * notification's badge lands on the icon on screen; its blue alias when
     * none is enabled.
     */
    fun active(context: Context, launcher: String): ComponentName {
        val pm = context.packageManager
        return IconTint.entries
            .map { tint -> alias(context, launcher, tint) }
            .firstOrNull { component -> enabled(pm, component) }
            ?: alias(context, launcher, IconTint.BLUE)
    }

    private fun alias(context: Context, launcher: String, tint: IconTint) =
        ComponentName(context, "${context.packageName}.$launcher${tint.suffix}")

    /** Whether [component] is on, reading the manifest's own setting when nothing overrode it. */
    private fun enabled(pm: PackageManager, component: ComponentName): Boolean =
        when (pm.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> try {
                pm.getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS).enabled
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
            else -> false
        }

    /**
     * How far apart two OKLCH colours sit on the colour wheel, with hue as
     * the angle and chroma as the radius.
     */
    private fun distance(hue: Float, chroma: Float, otherHue: Float, otherChroma: Float): Double {
        val first = Math.toRadians(hue.toDouble())
        val second = Math.toRadians(otherHue.toDouble())
        return hypot(
            chroma * cos(first) - otherChroma * cos(second),
            chroma * sin(first) - otherChroma * sin(second),
        )
    }
}
