// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.feeds.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The consent a source or an import needs names the app asking by its own
 * title: the server names it by its id, a long entity id on a server that
 * installed Feeds from the publisher.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PermissionRequestDialogTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `the dialog names Feeds by its title`() {
        rule.setContent { PermissionRequestDialog(permissionName = "Fetch from example.org", onAllow = {}, onDeny = {}) }
        rule.waitForIdle()
        val asking = context.getString(R.string.feeds_permission_request_message, context.getString(R.string.feeds_title))
        assertEquals(1, rule.onAllNodesWithText(asking).fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodesWithText("Fetch from example.org").fetchSemanticsNodes().size)
    }
}
