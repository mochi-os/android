// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.accounts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.settings.api.ConnectedAccount
import org.mochios.settings.api.Device
import org.mochios.settings.api.Provider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Lists of names read as the language writes a list, as the web's formatList does. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountListTest {

    @get:Rule
    val rule = createComposeRule()

    /** The transports used to be joined with ", " in every language. */
    @Test
    fun `a device's transports read as a list`() {
        rule.setContent {
            DeviceRow(
                device = Device(id = "phone", label = "Phone"),
                accounts = listOf(ConnectedAccount(type = "ntfy"), ConnectedAccount(type = "github")),
                onForget = {},
            )
        }
        rule.onNodeWithText("ntfy and GitHub").assertExists()
    }

    /** The grants used to be joined with ", " in every language. */
    @Test
    fun `an account's grants read as a list`() {
        rule.setContent {
            AccountRow(
                account = ConnectedAccount(id = "a", type = "google", granted = listOf("login", "calendar")),
                providers = listOf(Provider(type = "google", flow = "oauth")),
                onVerify = {},
                onSettings = {},
                onTest = {},
                onRemove = {},
                onToggleNotify = {},
                onSetAiDefault = {},
            )
        }
        rule.onNodeWithText("Sign-in and Calendar").assertExists()
    }

    /**
     * A network's account can notify, but its switch would turn the account
     * itself off, so Notify by default is never offered there.
     */
    @Test
    fun `a network's account offers no Notify by default switch, an email one does`() {
        val providers = listOf(
            Provider(type = "email", capabilities = listOf("notify")),
            Provider(type = "matrix", capabilities = listOf("network", "chat", "notify"), flow = "handle"),
        )
        var shown by mutableStateOf(ConnectedAccount(id = "m", type = "matrix", identifier = "@ada:example.org", enabled = 1))
        rule.setContent {
            AccountRow(
                account = shown,
                providers = providers,
                onVerify = {},
                onSettings = {},
                onTest = {},
                onRemove = {},
                onToggleNotify = {},
                onSetAiDefault = {},
            )
        }
        rule.onAllNodes(isToggleable()).assertCountEquals(0)
        shown = ConnectedAccount(id = "e", type = "email", identifier = "ada@example.org", enabled = 1)
        rule.onAllNodes(isToggleable()).assertCountEquals(1)
    }
}
