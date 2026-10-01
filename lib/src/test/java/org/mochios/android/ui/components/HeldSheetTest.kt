// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A sheet whose state holds it open, swiped down as a reader scrolling back up
 * through what they typed would swipe it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class HeldSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private var held by mutableStateOf(false)
    private var refusals = 0
    private var dismissed = false

    private fun show() {
        rule.setContent {
            MochiBottomSheet(
                onDismissRequest = { dismissed = true },
                sheetState = rememberHeldSheetState(held = { held }, onHeld = { refusals++ }),
            ) {
                Box(Modifier.fillMaxWidth().height(400.dp).testTag("content"))
            }
        }
        rule.waitForIdle()
    }

    private fun swipe() {
        rule.onNodeWithTag("content").performTouchInput { swipeDown() }
        rule.waitForIdle()
    }

    @Test
    fun `a swipe down closes a sheet that is not held`() {
        show()
        swipe()
        assertTrue(dismissed)
        assertEquals(0, refusals)
    }

    @Test
    fun `a swipe down leaves a held sheet open and reports the refusal`() {
        held = true
        show()
        swipe()
        assertFalse(dismissed)
        assertTrue("refused at least once", refusals > 0)
        rule.onNodeWithTag("content").assertExists()
    }

    @Test
    fun `a sheet no longer held closes on the next swipe`() {
        held = true
        show()
        swipe()
        held = false
        swipe()
        assertTrue(dismissed)
    }
}
