// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.account

import android.app.Activity
import android.content.Context
import android.view.WindowManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.settings.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The export section follows the build: it says so while the bundle is built,
 * saves a built one straight away, and a passphrase generated in its dialog
 * keeps the window out of screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DataSectionTest {

    @get:Rule
    val rule = createComposeRule()

    private fun text(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    @Test
    fun `while the bundle is built the section says so and takes no tap`() {
        rule.setContent { DataSection(export = Export.Building, onExport = {}, onSave = {}) }
        rule.onNodeWithText(text(R.string.account_data_preparing)).assertIsNotEnabled()
    }

    @Test
    fun `a built bundle is saved straight from the section`() {
        val saved = mutableListOf<String>()
        rule.setContent { DataSection(export = Export.Ready("b.zip"), onExport = {}, onSave = { saved += it }) }
        rule.onNodeWithText(text(R.string.account_data_download)).performClick()
        assertEquals(listOf("b.zip"), saved)
        rule.onNodeWithText(text(R.string.account_data_dialog_title)).assertDoesNotExist()
    }

    @Test
    fun `a generated passphrase keeps the window out of screenshots`() {
        var host: Context? = null
        rule.setContent {
            host = LocalContext.current
            DataSection(export = Export.None, onExport = {}, onSave = {})
        }
        val window = (host as Activity).window
        fun secure() = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE
        rule.onNodeWithText(text(R.string.account_data_download)).performClick()
        rule.onNodeWithText(text(R.string.account_data_dialog_title)).assertExists()
        assertEquals("a typed phrase is masked, so the window is not flagged", 0, secure())
        rule.onNodeWithContentDescription(text(R.string.account_data_generate)).performClick()
        rule.waitForIdle()
        assertEquals("THE POINT: kept out of screenshots, recordings and Recents", WindowManager.LayoutParams.FLAG_SECURE, secure())
    }
}
