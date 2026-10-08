// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * The shared copy: the text always reaches the clipboard, and the app says so
 * only where Android does not confirm a copy itself, in the screen's snackbar
 * when it has one and as a toast otherwise.
 */
@RunWith(RobolectricTestRunner::class)
class CopierTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    private fun clipboard(): String? {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return manager.primaryClip?.getItemAt(0)?.text?.toString()
    }

    private fun copier(snackbar: Boolean = false): Copier {
        lateinit var copier: Copier
        rule.setContent {
            val host = remember { SnackbarHostState() }
            copier = rememberCopier(if (snackbar) host else null)
            SnackbarHost(host)
        }
        rule.waitForIdle()
        return copier
    }

    @Test
    @Config(sdk = [32])
    fun `before Android 13 a copy says so in a toast`() {
        val copier = copier()
        rule.runOnIdle { copier.copy("abc") }
        rule.waitForIdle()
        assertEquals("abc", clipboard())
        assertEquals("Copied to clipboard", ShadowToast.getTextOfLatestToast())
    }

    @Test
    @Config(sdk = [32])
    fun `before Android 13 a screen with a snackbar says it there`() {
        val copier = copier(snackbar = true)
        rule.runOnIdle { copier.copy("abc", message = "RSS URL copied") }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("RSS URL copied").fetchSemanticsNodes().isNotEmpty()
        }
        assertNull(ShadowToast.getLatestToast())
    }

    @Test
    @Config(sdk = [32])
    fun `a quiet copy leaves the message to the caller`() {
        val copier = copier()
        rule.runOnIdle { copier.copy("abc", quiet = true) }
        rule.waitForIdle()
        assertEquals("abc", clipboard())
        assertNull(ShadowToast.getLatestToast())
    }

    @Test
    @Config(sdk = [35])
    fun `from Android 13 the system confirms the copy, so the app says nothing`() {
        val copier = copier()
        rule.runOnIdle { copier.copy("abc") }
        rule.waitForIdle()
        assertEquals("abc", clipboard())
        assertNull(ShadowToast.getLatestToast())
    }

    @Test
    @Config(sdk = [35])
    fun `a message that says more than the copy shows on every version`() {
        val copier = copier()
        rule.runOnIdle { copier.copy("abc", message = "New RSS URL copied", always = true) }
        rule.waitForIdle()
        assertEquals("New RSS URL copied", ShadowToast.getTextOfLatestToast())
    }
}
