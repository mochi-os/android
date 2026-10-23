// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.model

import org.mochios.android.sync.ContactProperty

/**
 * An address-book entry. [person] is the Mochi person entity id, empty for a
 * plain contact, and [friend] says whether that person is a friend. [name] is
 * the user's own label; [directory] is the directory's name for [person],
 * refreshed by the server and never written over the label.
 *
 * The list endpoint returns lean rows with no [card]; `-/contacts/get`,
 * `create` and `update` return the full row, where [card] is the lossless
 * vCard property list and [etag] guards the next write. [slug] is the
 * contact's name within its book: the one the sync adapter gave it on create,
 * else its id; empty from a server that predates it.
 */
data class Contact(
    val id: String = "",
    val book: String = "",
    val person: String = "",
    val friend: Boolean = false,
    val name: String = "",
    val directory: String = "",
    val created: Long = 0,
    val updated: Long = 0,
    val card: List<ContactProperty>? = null,
    val etag: String = "",
    val slug: String = "",
    /**
     * The contact's messaging address chat can reach, with the user's account
     * that reaches it; on list rows only, and null where chat reaches none.
     */
    val message: ContactMessage? = null,
    /**
     * How the contact stands on XMPP, for each XMPP address on the card and
     * each of the user's hosted XMPP accounts; only `-/contacts/get` sends it.
     */
    val xmpp: List<XmppContact> = emptyList(),
)

/** A messaging address ([address], as the card holds it) and the user's [account] that reaches it. */
data class ContactMessage(val address: String = "", val account: String = "")

/**
 * A contact on XMPP through one of the user's hosted XMPP accounts: whether
 * the account sees their presence (`to`), they see the account's (`from`),
 * both or neither, and the requests waiting either way. [identifier] is the
 * account's own address.
 */
data class XmppContact(
    val account: String = "",
    val identifier: String = "",
    val address: String = "",
    val subscription: String = "none",
    val ask: Boolean = false,
    val pending: Boolean = false,
) {
    /** The switch is on while the account sees their presence or has asked to. */
    val on: Boolean get() = subscription == "to" || subscription == "both" || ask

    /** Asked and not yet answered. */
    val invited: Boolean get() = ask && subscription != "to" && subscription != "both"
}

/** Where the friendship handshake stands for a contact, as the friend switch shows it. */
enum class FriendState { NONE, INVITED, FRIEND }

/** The handshake's state: a friend, an invitation sent and awaiting the other side, or neither. */
fun Contact.friendState(sent: Set<String>): FriendState = when {
    friend -> FriendState.FRIEND
    person.isNotBlank() && person in sent -> FriendState.INVITED
    else -> FriendState.NONE
}

/** A merge read before saving: the contact that survives, holding both cards, and the one it absorbs. */
data class ContactMerge(val contact: Contact, val source: Contact)
