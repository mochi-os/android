// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.profile

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.people.model.ProfileAccount
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The profile editor lists the accounts the profile shows, with the way to Settings › Networks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileAccountsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the accounts the profile shows are listed, and Settings opens Settings › Networks`() {
        val opened = mutableListOf<String>()
        rule.setContent {
            ProfileAccounts(
                listOf(
                    ProfileAccount(type = "mastodon", identifier = "@ada@mastodon.social", url = "https://mastodon.social/@ada"),
                    ProfileAccount(type = "bluesky", identifier = "ada.example", url = "javascript:alert(1)"),
                ),
                onOpenLink = { opened += it },
            )
        }
        // THE POINT: what Show on profile turns on is seen where the profile is edited.
        rule.onNodeWithText("Mastodon · @ada@mastodon.social").assertExists()
        rule.onNodeWithText("Bluesky · ada.example").assertDoesNotExist()
        rule.onNodeWithText("Settings").performClick()
        assertEquals(listOf("settings/user/networks"), opened)
    }

    @Test
    fun `nothing is shown when the profile shows no account`() {
        rule.setContent { ProfileAccounts(emptyList(), onOpenLink = {}) }
        rule.onNodeWithText("Networks").assertDoesNotExist()
        rule.onNodeWithText("Settings").assertDoesNotExist()
    }
}
