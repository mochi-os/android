// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(true, publishable(held, null, null))
        assertEquals(false, publishable(PublishState(), null, null))
        // A private feed's refusal is the tab's content.
        assertEquals(true, publishable(null, MochiError.ForbiddenError("Only a public feed publishes to other networks"), null))
    }

    @Test
    fun `Publish to is offered where the server offers a network to set an account up on`() {
        // THE POINT: an owner with no account yet is shown where to set one up.
        assertEquals(true, publishable(PublishState(offered = listOf("bluesky")), null, null))
    }

    @Test
    fun `the networks to set up are those offered that no account is on, a linked Mastodon account on the fediverse`() {
        val mastodon = PublishAccount(id = "m1", type = "mastodon", identifier = "@a@b.example")
        val bluesky = PublishAccount(id = "b1", type = "bluesky", identifier = "a.bsky.social", hosted = true)
        assertEquals(listOf("bluesky"), unset(listOf("fediverse", "bluesky"), listOf(mastodon)))
        assertEquals(listOf("fediverse"), unset(listOf("fediverse", "bluesky"), listOf(bluesky)))
        assertEquals(emptyList<String>(), unset(listOf("fediverse", "bluesky"), listOf(mastodon, bluesky)))
        assertEquals(listOf("fediverse", "bluesky"), unset(listOf("fediverse", "bluesky"), emptyList()))
    }

    @Test
    fun `Publish to is offered where reading it failed, so the tab shows the failure and a retry`() {
        // THE POINT: a failure other than a refusal keeps the tab, as on the web.
        assertEquals(true, publishable(null, null, MochiError.NetworkError()))
        assertEquals(true, publishable(PublishState(), null, MochiError.ServerError(502, "No networks here")))
    }

    @Test
    fun `Publish to is undecided until the server answers, so the tabs wait for it`() {
        assertNull(publishable(null, null, null))
    }
}
