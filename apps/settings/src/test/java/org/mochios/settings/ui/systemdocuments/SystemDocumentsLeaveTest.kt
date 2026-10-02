// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.systemdocuments

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.settings.R
import org.mochios.settings.api.SystemDocument
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.mochios.android.R as MochiR

/**
 * Leaving the documents editor by the top bar's arrow or the system back, and
 * turning the device, while the editor holds text that was never saved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class SystemDocumentsLeaveTest {

    @get:Rule
    val rule = createComposeRule()

    private val saved = SystemDocument(name = "rules", language = "en", body = "Be kind.", default = "Be kind.")
    private var state by mutableStateOf(
        SystemDocumentsUiState(
            isLoading = false,
            documents = listOf(SystemDocument(name = "rules", language = "en")),
            current = saved,
        ),
    )

    /** Times the screen asked to be closed. */
    private var left = 0

    /** Times a system back went past the screen, to where the app's navigation would take it. */
    private var passed = 0
    private lateinit var dispatcher: OnBackPressedDispatcher

    private fun text(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    @Composable
    private fun Screen() {
        // Registered before the screen's own handler, so it only sees a back the screen lets through.
        BackHandler { passed++ }
        dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
        SystemDocumentsEditor(
            state = state,
            snackbarHostState = remember { SnackbarHostState() },
            onBack = { left++ },
            onRetry = {},
            onTabChange = {},
            onLanguageChange = {},
            onOpen = { _, _ -> },
            onSave = { _, _, _ -> },
        )
    }

    private fun edit(from: String, to: String) {
        rule.onNode(hasSetTextAction() and hasText(from)).performTextReplacement(to)
        rule.waitForIdle()
    }

    private fun systemBack() {
        rule.runOnIdle { dispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private fun arrow() {
        rule.onNodeWithContentDescription(text(MochiR.string.common_back)).performClick()
        rule.waitForIdle()
    }

    private fun asking() = rule.onNodeWithText(text(R.string.system_documents_discard_title))

    /** The system back used to leave at once and drop the edit. */
    @Test
    fun `the system back with unsaved edits asks first, and leaves once they are discarded`() {
        rule.setContent { Screen() }
        edit("Be kind.", "Be kind, always.")
        systemBack()
        assertEquals(0, left)
        assertEquals(0, passed)
        asking().assertExists()

        rule.onNodeWithText(text(R.string.system_documents_discard)).performClick()
        rule.waitForIdle()
        assertEquals(1, left)
        asking().assertDoesNotExist()
    }

    @Test
    fun `the system back asks only while there are unsaved edits`() {
        rule.setContent { Screen() }
        edit("Be kind.", "Be kind, always.")
        systemBack()
        asking().assertExists()
        rule.onNodeWithText(text(MochiR.string.common_cancel)).performClick()
        rule.waitForIdle()
        asking().assertDoesNotExist()
        rule.onNode(hasSetTextAction() and hasText("Be kind, always.")).assertExists()

        edit("Be kind, always.", "Be kind.")
        systemBack()
        asking().assertDoesNotExist()
        assertEquals(1, passed)
        assertEquals(0, left)
    }

    /** The arrow used to leave at once and drop the edit. */
    @Test
    fun `the back arrow asks only while there are unsaved edits`() {
        rule.setContent { Screen() }
        edit("Be kind.", "Be kind, always.")
        arrow()
        assertEquals(0, left)
        asking().assertExists()
        rule.onNodeWithText(text(MochiR.string.common_cancel)).performClick()
        rule.waitForIdle()
        asking().assertDoesNotExist()
        assertEquals(0, left)
        rule.onNode(hasSetTextAction() and hasText("Be kind, always.")).assertExists()

        edit("Be kind, always.", "Be kind.")
        arrow()
        asking().assertDoesNotExist()
        assertEquals(1, left)
    }

    /** Turning the device used to put the saved text back. */
    @Test
    fun `an unsaved edit outlives a rotation, and gives way to a newly loaded document`() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent { Screen() }
        edit("Be kind.", "Be kind, always.")

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        rule.onNode(hasSetTextAction() and hasText("Be kind, always.")).assertExists()
        arrow()
        asking().assertExists()
        assertEquals(0, left)
        rule.onNodeWithText(text(MochiR.string.common_cancel)).performClick()
        rule.waitForIdle()

        // The save lands: the refetched body replaces the editor's text.
        state = state.copy(current = saved.copy(body = "Be kind, always. Signed."))
        rule.waitForIdle()
        rule.onNode(hasSetTextAction() and hasText("Be kind, always. Signed.")).assertExists()
        arrow()
        asking().assertDoesNotExist()
        assertEquals(1, left)
    }
}
