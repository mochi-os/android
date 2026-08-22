// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.theme

import androidx.compose.ui.graphics.Color

val Blue10 = Color(0xFF001A33)
val Blue20 = Color(0xFF003366)
val Blue30 = Color(0xFF004D99)
val Blue40 = Color(0xFF1976D2)
val Blue50 = Color(0xFF2196F3)
val Blue60 = Color(0xFF42A5F5)
val Blue70 = Color(0xFF64B5F6)
val Blue80 = Color(0xFF90CAF9)
val Blue90 = Color(0xFFBBDEFB)
val Blue95 = Color(0xFFE3F2FD)
val Blue99 = Color(0xFFF5F9FF)

val Neutral10 = Color(0xFF1A1C1E)
val Neutral20 = Color(0xFF2F3033)
val Neutral30 = Color(0xFF46474A)
val Neutral40 = Color(0xFF5E5F62)
val Neutral50 = Color(0xFF76777A)
val Neutral60 = Color(0xFF909194)
val Neutral70 = Color(0xFFABABAE)
val Neutral80 = Color(0xFFC7C6CA)
val Neutral90 = Color(0xFFE3E2E6)
val Neutral95 = Color(0xFFF1F0F4)
val Neutral99 = Color(0xFFFDFBFF)

// Container ramp for the fallback schemes, matching the tones
// ColorSchemeGenerator derives for a server theme.
val Neutral100 = Color(0xFFFFFFFF)
val Neutral12 = Color(0xFF1F2124)
val Neutral17 = Color(0xFF272A2D)
val Neutral22 = Color(0xFF35363A)
val Neutral24 = Color(0xFF3A3B3F)
val Neutral87 = Color(0xFFDBDADE)
val Neutral92 = Color(0xFFE9E7EC)
val Neutral94 = Color(0xFFEFEDF1)
val Neutral96 = Color(0xFFF4F3F7)
val Neutral98 = Color(0xFFFAF8FC)

val NeutralVariant20 = Color(0xFF2D3135)
val NeutralVariant30 = Color(0xFF43474B)
val NeutralVariant40 = Color(0xFF5B5E63)
val NeutralVariant50 = Color(0xFF74777C)
val NeutralVariant60 = Color(0xFF8E9196)
val NeutralVariant70 = Color(0xFFA8ABB1)
val NeutralVariant80 = Color(0xFFC4C6CC)
val NeutralVariant90 = Color(0xFFE0E2E8)

/** Error fill in light mode. Web's `#E7000B`. */
val ErrorRed = Color(0xFFE7000B)

/** Error fill in dark mode. Web's `#FF6467`. */
val ErrorRedDark = Color(0xFFFF6467)

/** Error container in light mode, and text over error surfaces in dark mode. */
val ErrorRedLight = Color(0xFFFFDBD5)

/** Text over a light error container. 6.39:1 on [ErrorRedLight]. */
val ErrorRedDeep = Color(0xFFA30005)

/** Text over [ErrorRedDark], and the error container in dark mode. 6.61:1. */
val ErrorRedOnDark = Color(0xFF2A0003)

// Diverging interest scale: red (−) ↔ grey (0) ↔ green (+). Hue carries the sign,
// saturation the strength, so neutral reads as plain grey rather than a colour you
// have to interpret. Keep in sync with interestColor() in lib/web/src/components/post-tags.tsx.
fun interestColor(interest: Double): Color {
    val magnitude = (kotlin.math.abs(interest) / 100.0).coerceIn(0.0, 1.0).toFloat()
    val hue = if (interest >= 0) 145f else 4f
    return Color.hsl(hue, 0.06f + magnitude * 0.72f, 0.50f - magnitude * 0.03f)
}
