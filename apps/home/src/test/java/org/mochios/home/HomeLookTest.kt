// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.ui.theme.oklch
import org.mochios.android.ui.theme.oklchOf
import org.mochios.home.ui.HomeBackground
import org.mochios.home.ui.HomePage
import org.mochios.home.ui.glowRadii
import org.mochios.home.ui.gradientLine
import org.mochios.home.ui.wordmarkColours
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.sqrt

/**
 * The home page looks as the web's does: the theme's primary glowing down
 * from the top, which the background preference turns off, and the "mochi"
 * wordmark shaded at 165 degrees from the primary to a lighter primary.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeLookTest {

    @get:Rule
    val rule = createComposeRule()

    private val white = Color(0xFFFFFFFF)
    private val blue = Color(0xFF3B82F6)

    /** The page's colour at its top centre and near its bottom, with the glow on or off. */
    private fun pixels(glow: Boolean): Pair<Color, Color> {
        rule.setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = blue, background = white)) {
                HomeBackground(glow = glow) { Box(Modifier.fillMaxSize()) }
            }
        }
        val map = rule.onRoot().captureToImage().toPixelMap()
        return Pair(map[map.width / 2, 1], map[map.width / 2, map.height - 2])
    }

    @Test
    fun `the primary glows down from the top and is gone below it`() {
        val (top, bottom) = pixels(glow = true)
        assertNotEquals(white, top)
        assertEquals(white, bottom)
    }

    @Test
    fun `with the background preference off there is no glow`() {
        val (top, _) = pixels(glow = false)
        assertEquals(white, top)
    }

    @Test
    fun `the page shows the wordmark with the glow behind its bar`() {
        // Every surface white too, so a bar painting its own colour would show.
        val scheme = lightColorScheme(primary = blue, background = white, surface = white, surfaceContainer = white)
        rule.setContent {
            MaterialTheme(colorScheme = scheme) {
                HomePage(glow = true, actions = {}) { Box(Modifier.fillMaxSize()) }
            }
        }
        rule.onNodeWithText("mochi").assertIsDisplayed()
        // The bar's corner, clear of the wordmark: the glow only shows there
        // when neither the page nor the bar paints over it.
        val map = rule.onRoot().captureToImage().toPixelMap()
        assertNotEquals(white, map[map.width / 2 - 150, 4])
    }

    @Test
    fun `the page's text takes the theme's colour on its background, light or dark`() {
        var scheme by mutableStateOf(lightColorScheme())
        var colour = Color.Unspecified
        rule.setContent {
            MaterialTheme(colorScheme = scheme) {
                HomePage(glow = true, actions = {}) { colour = LocalContentColor.current }
            }
        }
        rule.waitForIdle()
        assertEquals(scheme.onBackground, colour)
        scheme = darkColorScheme()
        rule.waitForIdle()
        assertEquals(darkColorScheme().onBackground, colour)
    }

    @Test
    fun `the glow is the CSS farthest-corner ellipse at the top edge`() {
        val (across, down) = glowRadii(400f, 420f)
        assertEquals(200f * sqrt(2f), across, 0.01f)
        assertEquals(420f * sqrt(2f), down, 0.01f)
    }

    @Test
    fun `a gradient at 180 degrees runs from top centre to bottom centre`() {
        val (start, end) = gradientLine(100f, 40f, 180f)
        assertNear(Offset(50f, 0f), start)
        assertNear(Offset(50f, 40f), end)
    }

    @Test
    fun `a gradient at 90 degrees runs from the left to the right`() {
        val (start, end) = gradientLine(100f, 40f, 90f)
        assertNear(Offset(0f, 20f), start)
        assertNear(Offset(100f, 20f), end)
    }

    @Test
    fun `the wordmark's 165 degree line matches CSS on a 200 by 40 box`() {
        // CSS: through the centre, length |200 sin 165| + |40 cos 165|.
        val (start, end) = gradientLine(200f, 40f, 165f)
        assertNear(Offset(88.30f, -23.66f), start)
        assertNear(Offset(111.70f, 63.66f), end)
    }

    @Test
    fun `the wordmark runs from the primary to the web's lighter primary`() {
        // The default Mochi theme's primary: hue 250, chroma 0.135. Its lighter
        // shade is inside sRGB, so it comes out exactly as the web's.
        val primary = oklch(0.55f, 0.135f, 250f)
        val (from, to) = wordmarkColours(primary)
        assertEquals(primary, from)
        val (_, chroma, hue) = oklchOf(primary)
        val (lightness, toChroma, toHue) = oklchOf(to)
        assertEquals(0.74f, lightness, 0.01f)
        assertEquals(chroma * 0.85f, toChroma, 0.01f)
        assertEquals(hue, toHue, 1f)
    }

    private fun assertNear(expected: Offset, actual: Offset) {
        assertEquals("x", expected.x, actual.x, 0.05f)
        assertEquals("y", expected.y, actual.y, 0.05f)
    }
}
