// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A ticket sheet's four tabs across a 384dp phone, at Android's large text size. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w420dp-h800dp", fontScale = 1.3f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MochiTabRowTest {

    @get:Rule
    val rule = createComposeRule()

    private val labels = listOf("Properties", "Comments (12)", "Merge requests (3)", "Activity")

    /** How wide "Properties" is drawn, against how wide it is on one unclipped line. */
    private fun widths(scrollable: Boolean): Pair<Float, Float> {
        var full = 0f
        rule.setContent {
            val measurer = rememberTextMeasurer()
            val style = MaterialTheme.typography.labelMedium
            full = with(LocalDensity.current) { measurer.measure(labels[0], style, maxLines = 1).size.width.toDp().value }
            Box(Modifier.width(384.dp)) {
                MochiTabRow(
                    tabs = labels.map { label -> MochiTab(label) },
                    selectedIndex = 0,
                    onSelect = {},
                    scrollable = scrollable,
                )
            }
        }
        rule.waitForIdle()
        val drawn = with(rule.density) {
            rule.onNodeWithText(labels[0], useUnmergedTree = true).fetchSemanticsNode().size.width.toDp().value
        }
        return drawn to full
    }

    @Test
    fun `a scrollable row gives each label the width it needs`() {
        val (drawn, full) = widths(scrollable = true)
        assertEquals(full, drawn, 1f)
    }

    @Test
    fun `a fixed row cuts a label longer than its share`() {
        // What the ticket sheet showed as "Propertie": a quarter of the row each.
        val (drawn, full) = widths(scrollable = false)
        assertTrue("drawn $drawn of $full", drawn < full - 1f)
    }
}
