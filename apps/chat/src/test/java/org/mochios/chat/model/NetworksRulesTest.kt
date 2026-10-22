// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.chat.ui.newchat.accountLabel

/**
 * The rules behind what chat shows of conversations on other networks and of
 * the signals Mochi's own conversations gained: ticks, who has read, who is
 * typing, addresses, and the encryption of Matrix and XMPP conversations.
 */
class NetworksRulesTest {

    private val me = "me"

    @Test
    fun `a message is read when every other member has read it, delivered when every one holds it, else sent`() {
        val receipts = listOf(
            ChatReceipt(member = me, delivered = 0, displayed = 0),
            ChatReceipt(member = "a", delivered = 100, displayed = 100),
            ChatReceipt(member = "b", delivered = 100, displayed = 50),
        )
        assertEquals(ReceiptState.READ, receiptState(50, receipts, me))
        assertEquals(ReceiptState.DELIVERED, receiptState(100, receipts, me))
        assertEquals(ReceiptState.SENT, receiptState(101, receipts, me))
        // The user's own watermark says nothing of how far the others are.
        assertEquals(ReceiptState.SENT, receiptState(1, listOf(ChatReceipt(member = me, displayed = 100)), me))
    }

    @Test
    fun `seen by names those who read the newest message of the user's, in order, a nameless receipt by its messages`() {
        val messages = listOf(
            ChatMessage(id = "1", member = me, created = 10),
            ChatMessage(id = "2", member = "z", name = "Zoe", created = 20),
            ChatMessage(id = "3", member = me, created = 30),
            ChatMessage(id = "4", member = me, created = 40, deleted = true),
        )
        val receipts = listOf(
            ChatReceipt(member = "z", name = "", displayed = 30),
            ChatReceipt(member = "a", name = "Adam", displayed = 35),
            ChatReceipt(member = "b", name = "Bea", displayed = 29),
            ChatReceipt(member = me, name = "Me", displayed = 40),
        )
        val seen = seenBy(messages, receipts, me)
        assertEquals("3", seen?.id)
        assertEquals(listOf("Adam", "Zoe"), seen?.names)
        assertNull(seenBy(messages, receipts.filter { it.member == "b" }, me))
        assertNull(seenBy(listOf(ChatMessage(id = "x", member = "z")), receipts, me))
    }

    @Test
    fun `a typist stands for the server's window, or as long as the event holds it, and goes when they stop`() {
        val typists = Typists()
        typists.add("a", "Ann", now = 0)
        typists.add("b", "Bob", now = 0, held = 30.0)
        assertEquals(listOf("Ann", "Bob"), typists.names(1_000))
        assertEquals(Typists.WINDOW, typists.next(1_000))
        assertEquals(listOf("Bob"), typists.names(Typists.WINDOW))
        assertEquals(30_000L, typists.next(Typists.WINDOW))
        typists.clear("b")
        assertEquals(emptyList<String>(), typists.names(Typists.WINDOW))
        assertNull(typists.next(Typists.WINDOW))
    }

    @Test
    fun `an address from Matrix or XMPP is read as typed or as a contact card gives it`() {
        assertEquals("@carol:matrix.example", matrixAddress(" matrix:u/carol:matrix.example "))
        assertEquals("@carol:matrix.example:8448", matrixAddress("@carol:matrix.example:8448"))
        assertNull(matrixAddress("carol:matrix.example"))
        assertEquals("dave@xmpp.example", xmppAddress("xmpp:Dave@XMPP.example"))
        assertNull(xmppAddress("dave@xmpp.example/phone"))
        assertNull(xmppAddress("Dave"))
    }

    @Test
    fun `a member search names someone from another network unless they are already a member`() {
        assertEquals("@carol:matrix.example", outsider("@carol:matrix.example", setOf("m1")))
        assertNull(outsider("@carol:matrix.example", setOf("@carol:matrix.example")))
        assertEquals("dave@xmpp.example", outsider("Dave@xmpp.example", emptySet()))
        // An XMPP person is kept under xmpp:, so that is what a member is known by.
        assertNull(outsider("dave@xmpp.example", setOf("xmpp:dave@xmpp.example")))
        assertNull(outsider("Carol", emptySet()))
    }

    @Test
    fun `addresses are taken by spaces or commas`() {
        assertEquals(listOf("@a@x.example", "@b@y.example", "@c@z.example"), addressList(" @a@x.example, @b@y.example\n@c@z.example,, "))
        assertEquals(emptyList<String>(), addressList(" , "))
    }

    @Test
    fun `which signals a conversation carries follows its network`() {
        val mochi = ChatDetail(members = listOf(ChatMember("me"), ChatMember("you")))
        assertTrue(mochi.receipts() && mochi.typing() && mochi.threads() && mochi.presence())
        val fediverse = mochi.copy(account = "f1", network = "fediverse")
        assertFalse(fediverse.receipts() || fediverse.typing() || fediverse.threads() || fediverse.presence())
        val xmpp = mochi.copy(account = "x1", network = "xmpp")
        assertTrue(xmpp.receipts() && xmpp.typing() && xmpp.presence())
        assertFalse(xmpp.threads())
        val matrix = mochi.copy(account = "m1", network = "matrix")
        assertTrue(matrix.threads())
        // A group shows no one's presence, and a conversation left takes nothing.
        assertFalse(mochi.copy(members = mochi.members + ChatMember("third")).presence())
        val left = mochi.copy(status = ChatStatus.LEFT)
        assertFalse(left.typing() || left.threads() || left.presence())
        assertTrue(left.receipts())
    }

    @Test
    fun `a group relays people from another network when one of its members is relayed`() {
        assertFalse(ChatDetail(members = listOf(ChatMember("a"), ChatMember("b"))).relayed())
        assertTrue(ChatDetail(members = listOf(ChatMember("a"), ChatMember("@c:m.example", relay = "a"))).relayed())
    }

    @Test
    fun `a fingerprint shows in groups of eight`() {
        assertEquals("abcdefgh ijklmnop qr", fingerprintGroups("abcdefghijklmnopqr"))
        assertEquals("", fingerprintGroups(""))
    }

    @Test
    fun `the verification shown is the latest still under way the user has not closed`() {
        val first = Verification(flow = "1", phase = "requested")
        val second = Verification(flow = "2", phase = "compare")
        val done = Verification(flow = "3", phase = "done")
        assertEquals("2", verificationShown(listOf(first, second, done), emptySet())?.flow)
        assertEquals("1", verificationShown(listOf(first, second, done), setOf("2"))?.flow)
        assertNull(verificationShown(listOf(done, Verification(flow = "4", phase = "cancelled")), emptySet()))
    }

    @Test
    fun `a device's trust is the user's word on it, else whether Matrix has it verified`() {
        assertEquals("distrusted", EncryptionDevice(trust = "distrusted", verified = true).trusted())
        assertEquals("verified", EncryptionDevice(verified = true).trusted())
        assertEquals("undecided", EncryptionDevice().trusted())
    }

    @Test
    fun `an account on another network is named by its network and address, a hosted fediverse one by its address`() {
        assertEquals("XMPP · me@xmpp.example", accountLabel(ChatAccount(identifier = "me@xmpp.example", network = "xmpp")))
        assertEquals("Matrix · @me:m.example", accountLabel(ChatAccount(identifier = "@me:m.example", network = "matrix")))
        assertEquals("@me@here.example", accountLabel(ChatAccount(identifier = "@me@here.example", network = "fediverse")))
    }
}
