// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/*
 * OKLCH to sRGB. Hue angles do not carry between colour spaces - OKLCH 255° is
 * blue where HSL 255° is violet - so a server hue must be converted, never
 * passed to Color.hsl. Conversion matrices are Björn Ottosson's Oklab reference
 * values.
 */

/** Iterations of the chroma bisection used to pull a colour into sRGB. */
private const val GAMUT_STEPS = 16

/** Slack on the in-gamut test, absorbing float error at the extremes. */
private const val GAMUT_EPSILON = 1e-4

/**
 * Builds an sRGB [Color] from OKLCH coordinates. A [chroma] the sRGB gamut
 * cannot hold is reduced until it fits, holding [lightness] and [hue]; [hue] is
 * wrapped, so callers may pass hue + 60.
 */
fun oklch(lightness: Float, chroma: Float, hue: Float): Color {
    val l = lightness.coerceIn(0f, 1f).toDouble()
    val requested = chroma.coerceAtLeast(0f).toDouble()
    val h = Math.toRadians(hue.toDouble().mod(360.0))

    var usable = requested
    if (!inGamut(l, requested, h)) {
        var low = 0.0
        var high = requested
        repeat(GAMUT_STEPS) {
            val mid = (low + high) / 2.0
            if (inGamut(l, mid, h)) low = mid else high = mid
        }
        usable = low
    }

    val (red, green, blue) = linearSrgb(l, usable, h)
    return Color(
        red = gammaEncode(red),
        green = gammaEncode(green),
        blue = gammaEncode(blue),
    )
}

/** The linear-sRGB components for an OKLCH colour, unclamped. */
private fun linearSrgb(l: Double, chroma: Double, hueRadians: Double): Triple<Double, Double, Double> {
    val a = chroma * cos(hueRadians)
    val b = chroma * sin(hueRadians)

    val lRoot = l + 0.3963377774 * a + 0.2158037573 * b
    val mRoot = l - 0.1055613458 * a - 0.0638541728 * b
    val sRoot = l - 0.0894841775 * a - 1.2914855480 * b

    val lCone = lRoot * lRoot * lRoot
    val mCone = mRoot * mRoot * mRoot
    val sCone = sRoot * sRoot * sRoot

    return Triple(
        4.0767416621 * lCone - 3.3077115913 * mCone + 0.2309699292 * sCone,
        -1.2684380046 * lCone + 2.6097574011 * mCone - 0.3413193965 * sCone,
        -0.0041960863 * lCone - 0.7034186147 * mCone + 1.7076147010 * sCone,
    )
}

private fun inGamut(l: Double, chroma: Double, hueRadians: Double): Boolean {
    val (red, green, blue) = linearSrgb(l, chroma, hueRadians)
    return red.isDisplayable() && green.isDisplayable() && blue.isDisplayable()
}

private fun Double.isDisplayable(): Boolean =
    this >= -GAMUT_EPSILON && this <= 1.0 + GAMUT_EPSILON

/** Linear light to the sRGB transfer curve. */
private fun gammaEncode(linear: Double): Float {
    val value = linear.coerceIn(0.0, 1.0)
    val encoded = if (value <= 0.0031308) {
        12.92 * value
    } else {
        1.055 * value.pow(1.0 / 2.4) - 0.055
    }
    return encoded.coerceIn(0.0, 1.0).toFloat()
}
