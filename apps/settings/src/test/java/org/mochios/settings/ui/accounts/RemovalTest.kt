// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.accounts

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.settings.api.ConnectedAccount

/**
 * Removing an account on another network is confirmed as the Networks screen
 * confirms it: a hosted one goes from its network with everything it
 * published, a linked one is only unlinked. Any other account keeps the plain
 * wording, and a sign-in provider's grants are revoked.
 */
class RemovalTest {

    // The accounts as the server lists them: only a network account carries
    // hosted, true or false.
    private fun parse(json: String): ConnectedAccount = Gson().fromJson(json, ConnectedAccount::class.java)

    @Test
    fun `a hosted account on another network is removed with what it published`() {
        val acc = parse("""{"id": "h1", "type": "fediverse", "identifier": "@me@here.example", "hosted": true}""")
        assertEquals(Removal.Hosted, removal(acc, revoking = false))
    }

    @Test
    fun `a linked account is unlinked, its server named`() {
        val acc = parse("""{"id": "l1", "type": "mastodon", "identifier": "@me@mastodon.social", "hosted": false, "server": "mastodon.social"}""")
        assertEquals(Removal.Linked, removal(acc, revoking = false))
        assertEquals("mastodon.social", acc.server)
    }

    @Test
    fun `any other account is plainly removed`() {
        val acc = parse("""{"id": "a1", "type": "claude", "identifier": "", "label": "Work Claude"}""")
        assertEquals(Removal.Plain, removal(acc, revoking = false))
    }

    @Test
    fun `a sign-in provider's grants are revoked`() {
        val acc = parse("""{"id": "g1", "type": "google", "identifier": "me@example.com"}""")
        assertEquals(Removal.Revoke, removal(acc, revoking = true))
    }
}
