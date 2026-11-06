// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.feeds.model.PublishAccount
import org.mochios.feeds.model.PublishState
import org.mochios.android.api.MochiError

/** Where a feed publishes is a tab of its own, before Access, shown only where there is somewhere to publish. */
class SettingsTabsTest {

    @Test
    fun `Publish to comes before Access where the feed has somewhere to publish`() {
        assertEquals(
            listOf(SettingsTab.General, SettingsTab.Publish, SettingsTab.Access, SettingsTab.Ai),
            settingsTabs(publishable = true, ai = true),
        )
        assertEquals(
            listOf(SettingsTab.General, SettingsTab.Publish, SettingsTab.Access),
            settingsTabs(publishable = true, ai = false),
        )
    }

    @Test
    fun `Publish to is left out where there is nowhere to publish`() {
        assertEquals(listOf(SettingsTab.General, SettingsTab.Access), settingsTabs(publishable = false, ai = false))
    }

    @Test
    fun `Publish to is offered where the owner holds an account, or the server says why not`() {
        val held = PublishState(accounts = listOf(PublishAccount(id = "m1", type = "mastodon", identifier = "@a@b.example")))
        assertTrue(publishable(held, null))
        assertFalse(publishable(PublishState(), null))
        assertFalse(publishable(null, null))
        // A private feed's refusal is the tab's content.
        assertTrue(publishable(null, MochiError.ForbiddenError("Only a public feed publishes to other networks")))
    }
}
