// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.theme

import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.R
import org.mochios.android.i18n.FontPref
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** "Dyslexia-friendly" gives Atkinson Hyperlegible, the face the web gives the same preference. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FontPreferenceTest {

    @Test
    fun `the dyslexia-friendly preference sets every style in Atkinson Hyperlegible`() {
        val typography = applyFontPreferences(MochiTypography, FontPref.DYSLEXIA, null)
        val styles = listOf(
            typography.displayLarge, typography.displayMedium, typography.displaySmall,
            typography.headlineLarge, typography.headlineMedium, typography.headlineSmall,
            typography.titleLarge, typography.titleMedium, typography.titleSmall,
            typography.bodyLarge, typography.bodyMedium, typography.bodySmall,
            typography.labelLarge, typography.labelMedium, typography.labelSmall,
        )
        for (style in styles) assertEquals(AtkinsonHyperlegibleFontFamily, style.fontFamily)
    }

    @Test
    fun `the family is the four bundled cuts`() {
        val fonts = (AtkinsonHyperlegibleFontFamily as FontListFontFamily).fonts.map { it as ResourceFont }
        assertEquals(
            listOf(
                Triple(R.font.atkinson_hyperlegible_regular, FontWeight.Normal, FontStyle.Normal),
                Triple(R.font.atkinson_hyperlegible_italic, FontWeight.Normal, FontStyle.Italic),
                Triple(R.font.atkinson_hyperlegible_bold, FontWeight.Bold, FontStyle.Normal),
                Triple(R.font.atkinson_hyperlegible_bold_italic, FontWeight.Bold, FontStyle.Italic),
            ),
            fonts.map { Triple(it.resId, it.weight, it.style) },
        )
        // Each is a TrueType file in the APK's resources.
        val resources = RuntimeEnvironment.getApplication().resources
        for (font in fonts) {
            val head = resources.openRawResource(font.resId).use { stream -> ByteArray(4).also { stream.read(it) } }
            assertArrayEquals(byteArrayOf(0, 1, 0, 0), head)
        }
    }
}
