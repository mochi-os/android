// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.domains

import android.content.ClipboardManager
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.android.i18n.AppContext
import org.mochios.android.util.fingerprint
import org.mochios.settings.R
import org.mochios.settings.api.Domain
import org.mochios.settings.api.Route
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.mochios.android.R as MochiR

/**
 * A domain's card, its verification record and the route editor. Plain
 * @Config: a MochiAlertDialog with text fields never idles under display
 * qualifiers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DomainCardTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private fun text(id: Int): String = context.getString(id)

    private val domain = Domain(domain = "a.example", verified = 0, token = "t0k3n")

    @Before
    fun begin() {
        AppContext.set(context)
    }

    private fun card(
        error: MochiError? = null,
        verifying: Boolean = false,
        onExpand: () -> Unit = {},
    ) {
        rule.setContent {
            DomainCard(
                domain = domain,
                details = null,
                isLoadingDetails = false,
                detailsError = error,
                isVerifying = verifying,
                isAdmin = true,
                apps = emptyList(),
                entities = emptyList(),
                userResults = emptyList(),
                onExpand = onExpand,
                onLoadRouteTargets = {},
                onUserSearch = {},
                onClearUserResults = {},
                onVerify = {},
                onTls = {},
                onDelete = {},
                onRouteCreate = { _, _, _, _ -> },
                onRouteUpdate = { _, _, _, _, _ -> },
                onRouteDelete = {},
                onDelegationCreate = { _, _ -> },
                onDelegationDelete = { _, _ -> },
            )
        }
    }

    /** An expanded card whose details failed to load used to be empty, with no way to try again. */
    @Test
    fun `a card whose details could not be read says why and reads them again on Retry`() {
        var reads = 0
        card(error = MochiError.ServerError(500, "Database locked"), onExpand = { reads++ })
        rule.onNodeWithContentDescription(text(MochiR.string.common_expand)).performClick()
        rule.waitForIdle()
        assertEquals(1, reads)
        rule.onNodeWithText("Database locked").assertExists()
        rule.onNodeWithText(text(MochiR.string.common_retry)).performClick()
        rule.waitForIdle()
        assertEquals(2, reads)
        rule.onNodeWithContentDescription(text(MochiR.string.common_collapse)).assertExists()
    }

    /** The record was two plain lines that could not be selected, so the token had to be retyped. */
    @Test
    fun `the verification record's name and value are copied with a tap`() {
        rule.setContent { VerificationBlock(domain = domain, isVerifying = false, onVerify = {}) }
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        rule.onNodeWithText("_mochi-verify.a.example").performClick()
        rule.waitForIdle()
        assertEquals("_mochi-verify.a.example", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        rule.onNodeWithText("mochi-verify=t0k3n").performClick()
        rule.waitForIdle()
        assertEquals("mochi-verify=t0k3n", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun `the verify button waits while the record is looked up`() {
        rule.setContent { VerificationBlock(domain = domain, isVerifying = true, onVerify = {}) }
        rule.onNodeWithText(text(R.string.domain_verify)).assertIsNotEnabled()
    }

    /** An edit used to open on "Select" until the lists loaded, and for good for another user's entity. */
    @Test
    fun `an edited route names its own target before the lists have loaded`() {
        val route = Route(domain = "a.example", path = "/blog", method = "entity", target = "e".repeat(50), targetName = "Bob's feed")
        rule.setContent {
            RouteDialog(initial = route, apps = emptyList(), entities = emptyList(), onDismiss = {}, onConfirm = { _, _, _, _, _ -> })
        }
        rule.onNodeWithText(pickerOf(R.string.route_target_entity, "Bob's feed")).assertExists()
    }

    private fun pickerOf(label: Int, value: String) = context.getString(R.string.route_picker_value, text(label), value)

    /** A route the server has no name for used to print its target's raw entity id. */
    @Test
    fun `a target with no name is shown by its fingerprint, never its id`() {
        val id = "1" + "a".repeat(49)
        assertEquals(fingerprint(id), routeTarget(Route(method = "entity", target = id)))
        assertEquals(fingerprint(id), routeTarget(Route(method = "app", target = id)))
        assertEquals("feeds", routeTarget(Route(method = "app", target = "feeds")))
        assertEquals("https://example.org/a", routeTarget(Route(method = "redirect", target = "https://example.org/a")))
        assertEquals("Feeds", routeTarget(Route(method = "app", target = id, targetName = "Feeds")))
    }
}
