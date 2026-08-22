// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import org.mochios.android.R

/**
 * The Google Fonts provider, backed by Play Services.
 *
 * The certificate array is Google's own, copied verbatim from the AOSP
 * downloadable-fonts sample; the provider will not serve a font to an app
 * whose certificates it cannot match, so these are not ours to invent or
 * shorten.
 */
private val GoogleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val Inter = GoogleFont("Inter")

private fun interWeight(weight: FontWeight) = Font(
    googleFont = Inter,
    fontProvider = GoogleFontProvider,
    weight = weight,
    style = FontStyle.Normal,
)

/**
 * The same weight cut from the variable face in the APK.
 *
 * One file answers all four: InterVariable carries a wght axis over 100-900,
 * so each weight is an instance of it rather than a font of its own, and a
 * fifth weight would cost nothing but a line here. The axis has to be set
 * explicitly - left off, every weight renders at the face's default 400 and
 * the heavier ones come out faked by the renderer rather than drawn.
 */
private fun bundledInterWeight(weight: FontWeight) = Font(
    resId = R.font.inter_variable,
    weight = weight,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/**
 * Inter, fetched from Google Fonts with the bundled face behind it.
 *
 * Each weight is listed twice, the downloadable cut first and the local one
 * second, which is how a font list says "use that one, fall back to this".
 * The provider is backed by Play Services, so the second entry is what a
 * device without GMS - a de-Googled build, an emulator image with no Play -
 * actually renders, and it renders Inter rather than whatever the device
 * calls its default. It covers the cold install too: the first frames draw
 * the bundled cut, so type no longer reflows once the download lands.
 *
 * The downloaded copy is still asked for first. The provider caches it across
 * apps and updates it without us shipping a release, neither of which the
 * file in the APK can do.
 *
 * The fallback itself needs no error path of ours. A downloadable font is an
 * async one, and Compose's own resolver - FontListFontFamilyTypefaceAdapter -
 * keeps the failure: it caches a load that failed as a permanent one, skips
 * that entry on every resolve after, and takes the next match in the family,
 * which is the bundled weight beneath it. The wait before that is capped at
 * Font.MaximumAsyncTimeoutMillis, fifteen seconds, so a provider that will
 * never answer stops being asked. Failures are swallowed rather than thrown,
 * so a device without the provider gets Inter from the APK, not a crash and
 * not a blank screen. Only a family with nothing left to try ends at the
 * platform typeface, and this one no longer can.
 */
val InterFontFamily = FontFamily(
    interWeight(FontWeight.Normal),
    bundledInterWeight(FontWeight.Normal),
    interWeight(FontWeight.Medium),
    bundledInterWeight(FontWeight.Medium),
    interWeight(FontWeight.SemiBold),
    bundledInterWeight(FontWeight.SemiBold),
    interWeight(FontWeight.Bold),
    bundledInterWeight(FontWeight.Bold),
)

/**
 * The Mochi type scale.
 *
 * Two deliberate departures from the stock Material 3 table, both aimed at the
 * flatness the default scale has on a dense productivity screen:
 *
 *  - Display and headline styles are Bold and title styles SemiBold, where
 *    Material leaves everything above bodyLarge at Normal. A screen whose
 *    heading is the same weight as its body has no entry point.
 *  - Tracking is negative on the large sizes and zero on body text. Material's
 *    positive body tracking (0.5sp on bodyLarge) is tuned for Roboto; carried
 *    over to Inter it reads loose and dated.
 */
val MochiTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-1.0).sp
    ),
    displayMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.75).sp
    ),
    displaySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp
    ),
    labelLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    )
)
