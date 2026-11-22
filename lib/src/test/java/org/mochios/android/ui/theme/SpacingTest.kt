// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.Density
import org.mochios.android.i18n.UserPreferences
import org.mochios.android.ui.components.CompactTextField
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiSheetHeader
import org.mochios.android.ui.components.MochiSheetPadding
import org.mochios.android.ui.components.Section
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Density preference sets the shared primitives' padding, gaps and control
 * heights by the web's factors, and leaves text alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
// Real text measurement, so a line of text is as tall as it is on a phone.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SpacingTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `each preset carries the web's values over comfortable`() {
        // core's style_preset_vars, in pixels at 16 to the rem.
        assertEquals(3.44f / 4.32f, Spacing.COMPACT.gap, 1e-4f)
        assertEquals(32f / 36f, Spacing.COMPACT.control, 1e-4f)
        assertEquals(20f / 24f, Spacing.COMPACT.horizontal, 1e-4f)
        assertEquals(14f / 16f, Spacing.COMPACT.vertical, 1e-4f)
        assertEquals(4.56f / 4.32f, Spacing.SPACIOUS.gap, 1e-4f)
        assertEquals(40f / 36f, Spacing.SPACIOUS.control, 1e-4f)
        assertEquals(28f / 24f, Spacing.SPACIOUS.horizontal, 1e-4f)
        assertEquals(20f / 16f, Spacing.SPACIOUS.vertical, 1e-4f)
    }

    @Test
    fun `the theme's own choice is comfortable`() {
        assertEquals(Spacing.COMFORTABLE, Spacing.of(Density.THEME))
        assertEquals(Spacing.COMFORTABLE, Spacing.of(null))
        assertEquals(Spacing.COMPACT, Spacing.of(Density.COMPACT))
        assertEquals(Spacing.SPACIOUS, Spacing.of(Density.SPACIOUS))
    }

    /** [content] in a theme at each density in turn, measured by [measure] at each. */
    private fun atDensities(content: @Composable () -> Unit, measure: () -> Float): Map<Density, Float> {
        var density by mutableStateOf(Density.COMFORTABLE)
        compose.setContent {
            MochiTheme(preferences = UserPreferences(density = density)) { content() }
        }
        return listOf(Density.COMFORTABLE, Density.COMPACT).associateWith { next ->
            compose.runOnIdle { density = next }
            compose.waitForIdle()
            measure()
        }
    }

    @Test
    fun `a sheet header's inset follows the preference`() {
        val inset = atDensities({ MochiSheetHeader(title = "Sheet", modifier = Modifier.testTag("header")) }) {
            (compose.onNodeWithText("Sheet").getBoundsInRoot().left - compose.onNodeWithTag("header").getBoundsInRoot().left).value
        }
        assertEquals(16f, inset.getValue(Density.COMFORTABLE), 0.5f)
        assertEquals("THE POINT: it narrows", 16f * Spacing.COMPACT.gap, inset.getValue(Density.COMPACT), 0.5f)
    }

    @Test
    fun `an empty state's gaps follow the preference`() {
        val gap = atDensities({
            EmptyState(icon = Icons.Default.Inbox, title = "Empty", verticalArrangement = Arrangement.Top, modifier = Modifier.testTag("empty"))
        }) {
            // The badge is 112dp; the title follows it after the gap.
            (compose.onNodeWithText("Empty").getBoundsInRoot().top - compose.onNodeWithTag("empty").getBoundsInRoot().top).value - 112f
        }
        assertEquals(16f, gap.getValue(Density.COMFORTABLE), 0.5f)
        assertEquals("THE POINT: it narrows", 16f * Spacing.COMPACT.gap, gap.getValue(Density.COMPACT), 0.5f)
    }

    @Test
    fun `a sheet's padding follows the preference, at its start in either direction`() {
        var sheet = PaddingValues()
        val bottom = atDensities({ sheet = MochiSheetPadding }) { sheet.calculateBottomPadding().value }
        assertEquals(24f, bottom.getValue(Density.COMFORTABLE), 0.01f)
        assertEquals("THE POINT: it narrows", 24f * Spacing.COMPACT.gap, bottom.getValue(Density.COMPACT), 0.01f)
        assertEquals(16f * Spacing.COMPACT.gap, sheet.calculateStartPadding(LayoutDirection.Rtl).value, 0.01f)
    }

    private class Measured(val inset: Dp, val field: Dp, val button: Dp, val wide: Dp, val text: Dp, val pixels: Float)

    @Test
    fun `the primitives follow the preference and text keeps its size`() {
        var density by mutableStateOf(Density.COMFORTABLE)
        var pixels = 0f
        compose.setContent {
            MochiTheme(preferences = UserPreferences(density = density)) {
                pixels = LocalDensity.current.density
                androidx.compose.foundation.layout.Column {
                    Text("Text", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("text"))
                    Section(title = "Title", modifier = Modifier.testTag("section")) {
                        Box(Modifier.size(10.dp).testTag("inside"))
                    }
                    // With text too small to set the height, so the control's own height is measured.
                    MaterialTheme(typography = Typography(bodyMedium = TextStyle(fontSize = 2.sp, lineHeight = 2.sp))) {
                        CompactTextField(value = "", onValueChange = {}, modifier = Modifier.fillMaxWidth().testTag("field"))
                    }
                    // Without the touch target's floor, which lays out every
                    // button at 48dp whatever it draws, so what it draws is
                    // measured; and with content too small to set the height.
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        MochiButton(onClick = {}, modifier = Modifier.testTag("button")) { Box(Modifier.size(width = 100.dp, height = 4.dp)) }
                    }
                }
            }
        }
        fun measure(): Measured {
            compose.waitForIdle()
            val section = compose.onNodeWithTag("section").getBoundsInRoot()
            val inside = compose.onNodeWithTag("inside").getBoundsInRoot()
            return Measured(
                inset = inside.left - section.left,
                field = compose.onNodeWithTag("field").getBoundsInRoot().let { it.bottom - it.top },
                button = compose.onNodeWithTag("button").getBoundsInRoot().let { it.bottom - it.top },
                wide = compose.onNodeWithTag("button").getBoundsInRoot().let { it.right - it.left },
                text = compose.onNodeWithTag("text").getBoundsInRoot().let { it.bottom - it.top },
                pixels = pixels,
            )
        }

        val comfortable = measure()
        assertEquals(16f, comfortable.inset.value, 0.5f)
        assertEquals(40f, comfortable.field.value, 0.5f)
        assertEquals(40f, comfortable.button.value, 0.5f)
        // Material's 24dp either side of the label.
        assertEquals(148f, comfortable.wide.value, 0.5f)

        compose.runOnIdle { density = Density.COMPACT }
        val compact = measure()
        assertEquals("text keeps its size", comfortable.text.value, compact.text.value, 0.01f)
        assertEquals("the screen's density is left alone", comfortable.pixels, compact.pixels)
        assertEquals("THE POINT: a card's padding narrows", 16f * 20f / 24f, compact.inset.value, 0.5f)
        // Material keeps the text a 24dp line, so the field is held at that and
        // its narrowed padding rather than at its compact height.
        assertTrue("a control is shorter", compact.field.value < comfortable.field.value - 2f)
        assertEquals("a button's padding narrows", 100f + 48f * Spacing.COMPACT.gap, compact.wide.value, 0.5f)

        compose.runOnIdle { density = Density.SPACIOUS }
        val spacious = measure()
        assertEquals("text keeps its size", comfortable.text.value, spacious.text.value, 0.01f)
        assertEquals("the screen's density is left alone", comfortable.pixels, spacious.pixels)
        assertEquals("THE POINT: a card's padding widens", 16f * 28f / 24f, spacious.inset.value, 0.5f)
        assertEquals("a control is taller", 40f * 40f / 36f, spacious.field.value, 0.5f)
        assertEquals("a button is taller", comfortable.button.value * 40f / 36f, spacious.button.value, 0.5f)
    }
}
