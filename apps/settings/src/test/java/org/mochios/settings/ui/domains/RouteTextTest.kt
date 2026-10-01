// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.domains

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.settings.R
import org.mochios.settings.api.Route
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RouteTextTest {

    @get:Rule
    val rule = createComposeRule()

    /** A picker's label and value used to be joined with an English colon. */
    @Test
    @Config(qualifiers = "fr")
    fun `a picker joins its field and value as the language writes it`() {
        var text = ""
        rule.setContent { text = pickerValue("Méthode", "Entité") }
        rule.waitForIdle()
        assertEquals("Méthode : Entité", text)
    }

    /** The arrow used to point back at the method in a right-to-left language. */
    @Test
    @Config(qualifiers = "ar")
    fun `a route's arrow points the way the language reads`() {
        var text = ""
        var method = ""
        rule.setContent {
            method = stringResource(R.string.route_method_entity)
            text = routeSummary(Route(method = "entity", target = "x", targetName = "Feeds"))
        }
        rule.waitForIdle()
        assertEquals("$method ← Feeds", text)
    }

    @Test
    fun `a route without a target name shows its target`() {
        var text = ""
        rule.setContent { text = routeSummary(Route(method = "redirect", target = "https://example.org")) }
        rule.waitForIdle()
        assertEquals("Redirect → https://example.org", text)
    }
}
