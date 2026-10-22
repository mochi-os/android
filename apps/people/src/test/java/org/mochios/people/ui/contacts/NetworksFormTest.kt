// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.android.sync.ContactProperty
import org.mochios.people.model.Contact
import org.mochios.people.model.ContactMessage
import org.mochios.people.model.FriendInvite
import org.mochios.people.model.ProfileHistory
import org.mochios.people.model.XmppContact
import org.mochios.people.sync.syncUpdate
import org.mochios.people.ui.invitations.inviteAvatar
import org.mochios.people.ui.invitations.receivedDetail
import org.mochios.people.ui.invitations.sentDetail
import org.mochios.people.ui.person.forumLink

/**
 * A contact's handles on other networks and messaging addresses, as the
 * editor reads and writes them, and the links and states the screens build
 * from what the server says of networks.
 */
class NetworksFormTest {

    private val card = listOf(
        ContactProperty("FN", emptyMap(), "Ada Lovelace"),
        ContactProperty("SOCIALPROFILE", mapOf("SERVICE-TYPE" to listOf("Fediverse")), "@ada@example.social", "item1"),
        ContactProperty("SOCIALPROFILE", mapOf("SERVICE-TYPE" to listOf("Bluesky")), "ada.bsky.social"),
        ContactProperty("IMPP", mapOf("TYPE" to listOf("home")), "xmpp:ada@example.org"),
        ContactProperty("IMPP", emptyMap(), "matrix:u/ada:example.org"),
        ContactProperty("IMPP", emptyMap(), "acct:ada@example.social"),
        ContactProperty("IMPP", emptyMap(), "sip:ada@example.org"),
        // A Matrix address some clients write without u/.
        ContactProperty("IMPP", emptyMap(), "matrix:@ada:example.net"),
    )

    @Test
    fun `handles and messaging addresses read into the form with their networks`() {
        val form = contactForm(card)
        assertEquals(listOf(NETWORK_FEDIVERSE, NETWORK_BLUESKY), form.handles.map { it.network })
        assertEquals("@ada@example.social", form.handles[0].value)
        assertEquals("item1", form.handles[0].group)
        assertEquals(
            listOf(NETWORK_XMPP, NETWORK_MATRIX, NETWORK_FEDIVERSE, NETWORK_OTHER, NETWORK_MATRIX),
            form.messaging.map { it.network },
        )
        assertEquals(
            listOf("ada@example.org", "@ada:example.org", "@ada@example.social", "sip:ada@example.org", "@ada:example.net"),
            form.messaging.map { it.value },
        )
    }

    @Test
    fun `handles and messaging addresses left alone are written back exactly as they came`() {
        val properties = contactForm(card).properties()
        assertEquals(card.filter { it.name != "FN" }, properties.filter { it.name != "FN" })
    }

    @Test
    fun `a handle's network comes from its service type, else from its shape`() {
        assertEquals(NETWORK_FEDIVERSE, handleNetwork(mapOf("SERVICE-TYPE" to listOf("Mastodon")), "ada.example"))
        assertEquals(NETWORK_BLUESKY, handleNetwork(mapOf("service-type" to listOf("bluesky")), "@ada@x"))
        assertEquals(NETWORK_FEDIVERSE, handleNetwork(emptyMap(), "@ada@example.social"))
        assertEquals(NETWORK_BLUESKY, handleNetwork(emptyMap(), "ada.bsky.social"))
    }

    @Test
    fun `a handle is written with the service type of the network chosen for it`() {
        val form = contactForm(card)
        val moved = form.copy(handles = listOf(form.handles[1].copy(network = NETWORK_FEDIVERSE)))
        val written = moved.properties().single { it.name == "SOCIALPROFILE" }
        assertEquals(listOf("Fediverse"), written.params["SERVICE-TYPE"])
        assertEquals("ada.bsky.social", written.value)
    }

    @Test
    fun `an address typed is written with the scheme its network takes`() {
        fun uri(network: String, value: String) = messagingUri(MessagingEntry(value = value, network = network))
        assertEquals("acct:ada@example.social", uri(NETWORK_FEDIVERSE, "@ada@example.social"))
        assertEquals("acct:ada@example.social", uri(NETWORK_FEDIVERSE, "acct:ada@example.social"))
        assertEquals("matrix:u/ada:example.org", uri(NETWORK_MATRIX, "@ada:example.org"))
        assertEquals("matrix:#room:example.org", uri(NETWORK_MATRIX, "#room:example.org"))
        assertEquals("xmpp:ada@example.org", uri(NETWORK_XMPP, "ada@example.org"))
        assertEquals("tel:+44", uri(NETWORK_OTHER, "tel:+44"))
        assertEquals("", uri(NETWORK_XMPP, "  "))
    }

    @Test
    fun `an address whose network changes is written afresh`() {
        val form = contactForm(card)
        val entry = form.messaging[1].copy(network = NETWORK_FEDIVERSE, value = "@ada@example.social")
        assertEquals("acct:ada@example.social", messagingUri(entry))
    }

    @Test
    fun `a contact's Message opens a friend's chat, else the conversation at their address`() {
        val friend = Contact(id = "c1", person = "p1", friend = true, name = "Ada")
        assertEquals("chat/new?friend=p1", messageLink(friend))
        val other = Contact(
            id = "c2",
            name = "Ada Lovelace",
            message = ContactMessage(address = "matrix:u/ada:example.org", account = "a1"),
        )
        assertEquals(
            "chat/new?account=a1&address=matrix%3Au%2Fada%3Aexample.org&name=Ada+Lovelace",
            messageLink(other),
        )
        assertNull(messageLink(Contact(id = "c3", name = "Nobody")))
        // A friend with an address is still messaged on Mochi.
        assertEquals("chat/new?friend=p1", messageLink(friend.copy(message = other.message)))
    }

    @Test
    fun `an XMPP contact is on while seen or asked, and invited only while the ask is open`() {
        assertFalse(XmppContact(subscription = "none").on)
        assertFalse(XmppContact(subscription = "from").on)
        assertTrue(XmppContact(subscription = "to").on)
        assertTrue(XmppContact(subscription = "both").on)
        assertTrue(XmppContact(subscription = "none", ask = true).on)
        assertTrue(XmppContact(subscription = "from", ask = true).invited)
        assertFalse(XmppContact(subscription = "both", ask = true).invited)
    }

    @Test
    fun `a request on XMPP is known by its address`() {
        val invite = FriendInvite(id = "xmpp:ada@example.org", name = "Ada", address = "ada@example.org", fingerprint = "")
        assertEquals("ada@example.org", receivedDetail(invite) { "listed as $it" })
        val mochi = FriendInvite(id = "p1", name = "Ada", fingerprint = "abcdefghi", directory = "Ada Byron")
        assertTrue(receivedDetail(mochi) { "listed as $it" }.endsWith("listed as Ada Byron"))
    }

    @Test
    fun `an invitation on XMPP shows its address and asks for no avatar`() {
        val xmpp = FriendInvite(id = "xmpp:ada@example.org", name = "Ada", address = "ada@example.org")
        assertEquals("Pending · ada@example.org", sentDetail(xmpp, "Pending"))
        assertNull(inviteAvatar(xmpp))
        val mochi = FriendInvite(id = "p1", name = "Ada")
        assertEquals("Pending", sentDetail(mochi, "Pending"))
        assertEquals("/people/p1/-/avatar", inviteAvatar(mochi))
    }

    @Test
    fun `an upload from the phone names the messaging addresses it carries, and not the handles it cannot`() {
        val managed = syncUpdate("c1", "e1", emptyList()).managed.orEmpty()
        assertTrue("IMPP" in managed)
        assertTrue("EMAIL" in managed)
        assertFalse("SOCIALPROFILE" in managed)
    }

    @Test
    fun `a forum post or comment opens under its forum`() {
        assertEquals("forums/fffffffff/p9", forumLink(ProfileHistory(kind = "comment", post = "p9", forum = "f1", fingerprint = "fffffffff")))
        assertEquals("forums/f1/p8", forumLink(ProfileHistory(kind = "post", post = "p8", forum = "f1")))
    }
}
