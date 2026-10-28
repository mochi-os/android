// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.networks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.settings.api.Handle
import org.mochios.settings.api.Handles
import org.mochios.settings.api.NetworkAccount
import org.mochios.settings.api.NetworkFeed
import org.mochios.settings.api.NetworksData
import org.mochios.settings.api.OtherToken
import org.mochios.settings.ui.sessions.recentFirst

/** The rules the Networks screen shares with the web page. */
class NetworksRulesTest {

    @Test
    fun `a handle is suggested from the name, unaccented, starting with a letter`() {
        assertEquals("josealvarez", suggestHandle("José Álvarez"))
        assertEquals("ab12", suggestHandle("12ab12"))
        assertEquals("", suggestHandle("Al"))
        assertEquals("abcdefghijklmnopqrst", suggestHandle("abcdefghijklmnopqrstuvwxyz"))
    }

    @Test
    fun `the cards are the handles under no other, and a card's names are those under it on its domain`() {
        val alice = Handle(domain = "a.example", name = "alice", kind = "handle")
        val zed = Handle(domain = "a.example", name = "Zed", kind = "handle")
        val news = Handle(domain = "a.example", name = "alice_news", parent = "alice", kind = "handle")
        val elsewhere = Handle(domain = "b.example", name = "alice_art", parent = "alice", kind = "handle")
        val community = Handle(domain = "a.example", name = "club", kind = "community")
        val all = listOf(zed, news, community, alice, elsewhere)
        assertEquals(listOf("alice", "Zed"), parentHandles(all).map { it.name })
        assertEquals(listOf("alice_news"), childHandles(all, alice).map { it.name })
    }

    @Test
    fun `a handle may be reserved only where a network is hosted and the limit allows`() {
        val data = NetworksData(networks = listOf("fediverse"), handles = Handles(limit = 1, counted = 0))
        assertTrue(mayReserve(data))
        assertFalse(mayReserve(data.copy(handles = Handles(limit = 1, counted = 1))))
        assertTrue(mayReserve(data.copy(handles = Handles(limit = 1, counted = 1, unlimited = true))))
        assertFalse(mayReserve(data.copy(networks = emptyList())))
    }

    @Test
    fun `a network is switched on for a name when a hosted account has its address there`() {
        val handle = Handle(name = "alice", addresses = mapOf("fediverse" to "@alice@a.example"))
        val hosted = NetworkAccount(id = "1", type = "fediverse", hosted = true, identifier = "@alice@a.example")
        val linked = hosted.copy(id = "2", hosted = false)
        assertEquals("1", hostedAccount(listOf(linked, hosted), handle, "fediverse")?.id)
        assertNull(hostedAccount(listOf(linked), handle, "fediverse"))
        assertNull(hostedAccount(listOf(hosted), handle, "bluesky"))
    }

    @Test
    fun `another app is given the bare domain for XMPP and an address otherwise`() {
        assertEquals("a.example", serverAddress(NetworkAccount(type = "xmpp", domain = "a.example")))
        assertEquals("https://a.example", serverAddress(NetworkAccount(type = "fediverse", domain = "a.example")))
        assertEquals("https://s.example", serverAddress(NetworkAccount(type = "bluesky", server = "s.example")))
        assertEquals("", serverAddress(NetworkAccount(type = "matrix")))
    }

    @Test
    fun `only a hosted fediverse or Bluesky account of a person has a home feed`() {
        assertTrue(homed(NetworkAccount(type = "fediverse", hosted = true)))
        assertTrue(homed(NetworkAccount(type = "bluesky", hosted = true)))
        assertFalse(homed(NetworkAccount(type = "fediverse", hosted = true, kind = "group")))
        assertFalse(homed(NetworkAccount(type = "xmpp", hosted = true)))
        assertFalse(homed(NetworkAccount(type = "fediverse", hosted = false)))
    }

    @Test
    fun `an account's card says the most pressing thing about it`() {
        val feeds = listOf(NetworkFeed(id = "f", privacy = "private"))
        val base = NetworkAccount(type = "fediverse", hosted = true, server = "a.example")
        assertEquals(AccountStatus.Failed("refused"), accountStatus(base.copy(error = "refused", moved = "x"), feeds))
        assertEquals(AccountStatus.Moved("@a@b"), accountStatus(base.copy(moved = "@a@b", home = "gone"), feeds))
        assertEquals(AccountStatus.HomeDeleted, accountStatus(base.copy(home = "gone"), feeds))
        assertEquals(AccountStatus.HomePrivate, accountStatus(base.copy(home = "f"), feeds))
        assertEquals(AccountStatus.NoRecovery, accountStatus(base.copy(type = "bluesky"), feeds))
        assertEquals(AccountStatus.Server("a.example"), accountStatus(base.copy(type = "bluesky", recovery = true), feeds))
        assertEquals(AccountStatus.Connected, accountStatus(NetworkAccount(type = "matrix", connected = true), feeds))
        assertEquals(AccountStatus.Disconnected, accountStatus(NetworkAccount(type = "xmpp"), feeds))
        assertEquals(AccountStatus.Server("a.example"), accountStatus(base, feeds))
    }

    @Test
    fun `sign-ins and passwords are listed most recently used first, then most recently made`() {
        val tokens = listOf(
            OtherToken(hash = "old", used = 10, created = 1),
            OtherToken(hash = "unused", used = 0, created = 50),
            OtherToken(hash = "recent", used = 20, created = 2),
            OtherToken(hash = "unused-new", used = 0, created = 60),
        )
        assertEquals(listOf("recent", "old", "unused-new", "unused"), recentFirst(tokens).map { it.hash })
    }
}
