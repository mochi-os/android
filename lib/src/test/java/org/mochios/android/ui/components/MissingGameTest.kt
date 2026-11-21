// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.R
import org.mochios.android.i18n.AppContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A game deleted from another device: the stored id is forgotten, so the next
 * launch does not open it again. One the launch reopened gives way to the
 * empty pane; one a link or a notification opened says it is gone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MissingGameTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun begin() {
        AppContext.set(context)
        LastViewedStore.forget(context)
    }

    @Test
    fun `the router's game is marked as reopened, once`() {
        LastViewedStore.set(context, "chess", "g1")
        assertEquals("g1", LastViewedStore.restore(context, "chess"))
        assertFalse(LastViewedStore.reopened("chess", "g2"))
        assertTrue(LastViewedStore.reopened("chess", "g1"))
        assertFalse("a later opening was asked for", LastViewedStore.reopened("chess", "g1"))
        LastViewedStore.clear(context, "chess")
        assertEquals(LastViewedStore.ALL, LastViewedStore.restore(context, "chess"))
    }

    @Test
    fun `a reopened game that is gone gives way to the empty pane`() {
        LastViewedStore.set(context, "go", "g1")
        LastViewedStore.restore(context, "go")
        var emptied = 0
        rule.setContent {
            val reopened = rememberReopened("go", "g1")
            MissingGame(feature = "go", id = "g1", reopened = reopened, onEmpty = { emptied += 1 })
        }
        rule.waitForIdle()
        assertEquals("THE POINT: no error at every launch", 1, emptied)
        assertNull(LastViewedStore.get(context, "go"))
        rule.onNodeWithText(context.getString(R.string.game_not_found)).assertDoesNotExist()
    }

    @Test
    fun `a linked game that is gone says so, with a way back`() {
        LastViewedStore.set(context, "words", "g1")
        var emptied = 0
        rule.setContent {
            val reopened = rememberReopened("words", "g1")
            MissingGame(feature = "words", id = "g1", reopened = reopened, onEmpty = { emptied += 1 })
        }
        rule.waitForIdle()
        assertNull("THE POINT: forgotten", LastViewedStore.get(context, "words"))
        assertEquals(0, emptied)
        rule.onNodeWithText(context.getString(R.string.game_not_found)).assertExists()
        rule.onNodeWithText(context.getString(R.string.common_back)).performClick()
        assertEquals(1, emptied)
    }

    @Test
    fun `another game's stored id is kept`() {
        LastViewedStore.set(context, "chess", "g2")
        rule.setContent {
            MissingGame(feature = "chess", id = "g1", reopened = false, onEmpty = {})
        }
        rule.waitForIdle()
        assertEquals("g2", LastViewedStore.get(context, "chess"))
    }
}
