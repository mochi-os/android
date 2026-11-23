// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.login

import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.settings.R
import org.mochios.settings.api.OAuthIdentity
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * Unlinking a network's sign-in names the account it signs in with and says
 * the account stays linked; the shown-once recovery codes close only from Done.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LoginDialogsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun text(id: Int, vararg args: Any): String = RuntimeEnvironment.getApplication().getString(id, *args)

    private fun unlinking(identity: OAuthIdentity) {
        rule.setContent { Text(unlinkMessage(identity)) }
    }

    @Test
    fun `unlinking a network's sign-in names its account and says the account stays`() {
        unlinking(OAuthIdentity(provider = "mastodon", account = "@alice@mastodon.example"))
        rule.onNodeWithText(text(R.string.account_oauth_unlink_network, "@alice@mastodon.example")).assertExists()
    }

    @Test
    fun `a network's sign-in from a core that names no account reads as any other`() {
        unlinking(OAuthIdentity(provider = "bluesky"))
        rule.onNodeWithText(text(R.string.account_oauth_unlink_message, "Bluesky")).assertExists()
    }

    @Test
    fun `a network's sign-in is shown as its account, and its unlink says the account stays`() {
        rule.setContent {
            // A column, as on the screen: the section's rows are siblings.
            Column {
                OAuthSection(
                    identities = listOf(
                        OAuthIdentity(provider = "mastodon", email = "net@example.com", account = "@alice@mastodon.example"),
                        OAuthIdentity(provider = "github", email = "alice@example.com"),
                    ),
                    providers = emptyList(),
                    onUnlink = {},
                    onLink = {},
                )
            }
        }
        rule.onNodeWithText("@alice@mastodon.example").assertExists()
        rule.onNodeWithText("net@example.com").assertDoesNotExist()
        rule.onNodeWithText("alice@example.com").assertExists()
        rule.onAllNodesWithText(text(R.string.account_unlink))[0].performClick()
        rule.onNodeWithText(text(R.string.account_oauth_unlink_network, "@alice@mastodon.example")).assertExists()
    }

    @Test
    fun `unlinking another provider names it, as the library names providers`() {
        unlinking(OAuthIdentity(provider = "github"))
        rule.onNodeWithText(text(R.string.account_oauth_unlink_message, "GitHub")).assertExists()
    }

    @Test
    fun `the recovery codes are not lost to Back, only closed by Done`() {
        var done = 0
        rule.setContent { RecoveryCodesDialog(codes = listOf("abcd-efgh"), onCopyAll = {}, onDone = { done++ }) }
        rule.onNodeWithText("abcd-efgh").assertExists()
        val dialog = ShadowDialog.getLatestDialog() as ComponentDialog
        rule.runOnUiThread { dialog.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertEquals("THE POINT: Back does not close the codes", 0, done)
        assertTrue(dialog.isShowing)
        rule.onNodeWithText(text(R.string.account_done)).performClick()
        assertEquals(1, done)
    }
}
