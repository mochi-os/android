// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.list

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.android.i18n.AppContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException
import org.mochios.android.R as MochiR

/**
 * The wiki list's feed URL: a copy or a revoke that failed says the server's
 * reason, and says it failed rather than borrowing another action's line.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RssMessagesTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun begin() = AppContext.set(context)

    @Test
    fun `a revoke that failed says the server's reason, its own line without one, and done when it worked`() {
        assertEquals("Revoked", revokeMessage(null, revoked = "Revoked", failed = "Could not revoke"))
        assertEquals("Token store locked", revokeMessage(MochiError.Unknown("Token store locked", 409), revoked = "Revoked", failed = "Could not revoke"))
        assertEquals("Could not revoke", revokeMessage(MochiError.ServerError(500), revoked = "Revoked", failed = "Could not revoke"))
        assertEquals(context.getString(MochiR.string.error_network), revokeMessage(IOException("offline"), revoked = "Revoked", failed = "Could not revoke"))
    }

    @Test
    fun `a copy that failed says the server's reason`() {
        val host = SnackbarHostState()
        lateinit var scope: CoroutineScope
        rule.setContent {
            scope = rememberCoroutineScope()
            SnackbarHost(host)
        }
        rule.runOnIdle {
            scope.launch {
                copyRss(
                    mode = "changes",
                    regenerate = false,
                    makeUrl = { _, _ -> Result.failure(MochiError.Unknown("Feeds are off on this server", 403)) },
                    copy = { _, _ -> },
                    snackbar = host,
                    existsMessage = "Exists",
                    replaceLabel = "Replace",
                    failedMessage = "Failed to get RSS token",
                )
            }
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Feeds are off on this server").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("Failed to get RSS token").assertCountEquals(0)
    }
}
