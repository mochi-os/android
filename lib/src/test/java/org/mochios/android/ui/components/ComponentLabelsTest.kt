// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.update.DownloadState
import org.mochios.android.update.UpdateChecker
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * What the shared chrome says and allows: the search bar's clear button and
 * the placeholder's menu button are named for what they do, a create screen's
 * submit is off while its request runs, and the update download's percentage
 * is written as the language writes one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class ComponentLabelsTest {

    @get:Rule
    val rule = createComposeRule()

    @After
    fun end() {
        downloads().value = DownloadState.Idle
    }

    /** The process-wide download state the About dialog shows. */
    @Suppress("UNCHECKED_CAST")
    private fun downloads(): MutableStateFlow<DownloadState> {
        val field = UpdateChecker::class.java.getDeclaredField("_state")
        field.isAccessible = true
        return field.get(null) as MutableStateFlow<DownloadState>
    }

    @Test
    fun `the search bar's clear button says it clears`() {
        rule.setContent {
            MochiSearchTopBar(query = "tea", placeholder = "Search", onQueryChange = {}, onClose = {})
        }
        rule.onNodeWithContentDescription("Clear search").assertExists()
        rule.onNodeWithContentDescription("Back").assertExists()
        rule.onNodeWithContentDescription("Close").assertDoesNotExist()
    }

    @Test
    fun `the placeholder's menu button says it opens the menu`() {
        rule.setContent { DrawerPlaceholderScreen(title = "Feeds", message = "Pick a feed", onOpenDrawer = {}) }
        rule.onNodeWithContentDescription("Open menu").assertExists()
        rule.onNodeWithContentDescription("Feeds").assertDoesNotExist()
    }

    private fun scaffold(busy: Boolean) {
        rule.setContent {
            CreateEntityScaffold(
                title = "New feed",
                submitLabel = "Create",
                submitEnabled = true,
                isBusy = busy,
                error = null,
                onBack = {},
                onSubmit = {},
            ) { Text("Body") }
        }
    }

    @Test
    fun `a create screen's submit is off while its request runs, whatever the caller passes`() {
        // The busy button's spinner never lets the clock settle.
        rule.mainClock.autoAdvance = false
        scaffold(busy = true)
        rule.mainClock.advanceTimeBy(250)
        // Back and submit.
        rule.onAllNodes(hasClickAction()).assertCountEquals(2)
        rule.onAllNodes(hasClickAction()).assertAll(isNotEnabled())
    }

    @Test
    fun `a create screen's submit is on when the caller allows it and nothing runs`() {
        scaffold(busy = false)
        rule.onNodeWithText("Create").assertIsEnabled()
    }

    @Test
    @Config(qualifiers = "+tr")
    fun `the download percentage puts the sign where the language does`() {
        downloads().value = DownloadState.Running("2.0", downloaded = 42, total = 100)
        rule.setContent { AboutDialog(onDismiss = {}) }
        rule.onNodeWithText("%42").assertExists()
    }

    @Test
    @Config(qualifiers = "+fr")
    fun `the download percentage keeps French's space before the sign`() {
        downloads().value = DownloadState.Running("2.0", downloaded = 42, total = 100)
        rule.setContent { AboutDialog(onDismiss = {}) }
        rule.onNodeWithText("42 %").assertExists()
    }
}
