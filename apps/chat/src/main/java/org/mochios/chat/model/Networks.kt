// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.model

import org.mochios.android.util.NaturalCompare

/*
 * Conversations on other networks, and what Mochi's own conversations gained
 * beside them: typing, receipts, presence, threads, quotes, and the encryption
 * of Matrix and XMPP conversations, whose devices the server holds.
 */

/** The networks a linked or hosted XMPP or Matrix account is on, which carry typing, read markers and presence. */
fun chatLinked(network: String): Boolean = network == "xmpp" || network == "matrix"

/** Whether a conversation is one the user is in and may write to. */
fun ChatDetail.active(): Boolean = status == ChatStatus.ACTIVE

/** Whether the members' ticks show: on Mochi, and on a linked XMPP or Matrix conversation. */
fun ChatDetail.receipts(): Boolean = account.isEmpty() || chatLinked(network)

/** Whether the user's typing goes out: as [receipts], while the conversation is active. */
fun ChatDetail.typing(): Boolean = active() && (account.isEmpty() || chatLinked(network))

/** Whether a message's thread opens: on Mochi and on Matrix, the networks with threads. */
fun ChatDetail.threads(): Boolean = active() && (account.isEmpty() || network == "matrix")

/** Whether the conversation is a one-to-one, which shows the other person's presence. */
fun ChatDetail.direct(): Boolean = members.size in 1..2

/** Whether the other member's presence is asked: a one-to-one on Mochi, XMPP or Matrix. */
fun ChatDetail.presence(): Boolean = active() && direct() && (account.isEmpty() || chatLinked(network))

/** Whether people from Matrix or XMPP are relayed into the group, unencrypted. */
fun ChatDetail.relayed(): Boolean = members.any { it.relay.isNotEmpty() }

/** A person on another network that a quoted post names. */
data class QuoteAuthor(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val avatar: String = "",
    val url: String = "",
)

/** The post on another network a message's first link quotes, as the writer saw it. */
data class ChatQuote(
    val network: String = "",
    val url: String = "",
    val id: String = "",
    val text: String = "",
    val image: String = "",
    val created: Long = 0,
    val author: QuoteAuthor? = null,
)

/** How far a member has received and read the conversation: every message up to each time. */
data class ChatReceipt(
    val member: String = "",
    val name: String = "",
    val delivered: Long = 0,
    val displayed: Long = 0,
)

/** Whether another member is online, as they share it, and when they were last seen. */
data class ChatPresence(
    val member: String = "",
    /** online, away, busy, offline, or empty where their network said nothing. */
    val status: String = "",
    val message: String = "",
    val seen: Long = 0,
)

/** One of the user's accounts on another network a conversation can be had through. */
data class ChatAccount(
    val id: String = "",
    val identifier: String = "",
    val network: String = "",
)

/** Who may start a chat with the user, and what the user shares, each on until turned off. */
data class ChatPreferences(
    val policy: String = "friends",
    val presence: Boolean = true,
    val receipts: Boolean = true,
    val typing: Boolean = true,
)

/** How far the other members have the user's own message. */
enum class ReceiptState { SENT, DELIVERED, READ }

/**
 * How far the other members have a message of the user's sent at [created]:
 * read when every one has read up to it, delivered when every one holds it,
 * else sent.
 */
fun receiptState(created: Long, receipts: List<ChatReceipt>, me: String): ReceiptState {
    val others = receipts.filter { it.member != me }
    if (others.isEmpty()) return ReceiptState.SENT
    if (others.all { it.displayed >= created }) return ReceiptState.READ
    if (others.all { it.delivered >= created }) return ReceiptState.DELIVERED
    return ReceiptState.SENT
}

/** Who in a group has read the user's newest message: its id and their names. */
data class Seen(val id: String, val names: List<String>)

/**
 * In a group, who has read the user's newest message, by name in order; null
 * while nobody has. A receipt with no name takes the name its member's
 * messages carry.
 */
fun seenBy(messages: List<ChatMessage>, receipts: List<ChatReceipt>, me: String): Seen? {
    val newest = messages.lastOrNull { it.member == me && !it.deleted } ?: return null
    val names = receipts
        .filter { it.member != me && it.displayed >= newest.created }
        .map { receipt -> receipt.name.ifEmpty { messages.firstOrNull { it.member == receipt.member }?.name.orEmpty() } }
        .filter { it.isNotEmpty() }
        .sortedWith(NaturalCompare)
    return if (names.isEmpty()) null else Seen(newest.id, names)
}

/**
 * Who is typing in a conversation, from the typing events the websocket
 * brings. Each stands for the server's typing window from when it came, or
 * for as long as the event holds it where another network says when typing
 * stops, and goes at once when that member's message arrives or they stop.
 */
class Typists {
    private data class Typist(val name: String, val until: Long)

    private val typists = linkedMapOf<String, Typist>()

    /** [member] is typing as of [now], in milliseconds, for [held] seconds where the event says. */
    fun add(member: String, name: String, now: Long, held: Double? = null) {
        val window = if (held != null && held > 0) (held * 1000).toLong() else WINDOW
        typists[member] = Typist(name, now + window)
    }

    fun clear(member: String) {
        typists.remove(member)
    }

    /** The names of those typing at [now]. */
    fun names(now: Long): List<String> = typists.values.filter { it.until > now }.map { it.name }

    /** When the next of them lapses, or null where none is typing. */
    fun next(now: Long): Long? = typists.values.map { it.until }.filter { it > now }.minOrNull()

    companion object {
        /** The server's typing window. */
        const val WINDOW = 8_000L
    }
}

/** How often typing is told while the user writes; the server's window is twice it. */
const val TYPING_INTERVAL = 4_000L

/** How often a one-to-one conversation asks whether the other person is online. */
const val PRESENCE_INTERVAL = 60_000L

// A Matrix address, @name:server.
private val MATRIX_ADDRESS = Regex("^@[^\\s@:/]{1,255}:[A-Za-z0-9.-]+(:[0-9]+)?$")

// An XMPP address, name@server.
private val XMPP_ADDRESS = Regex("^[^\\s@/]{1,500}@[A-Za-z0-9.-]+$")

/**
 * The Matrix address a search names, @name:server, as typed or as a contact
 * card's messaging address gives it (matrix:u/name:server); null for anything
 * else.
 */
fun matrixAddress(value: String): String? {
    val text = value.trim()
    val address = if (text.lowercase().startsWith("matrix:u/")) "@" + text.substring("matrix:u/".length) else text
    return address.takeIf { MATRIX_ADDRESS.matches(it) }
}

/**
 * The XMPP address a search names, name@server, as typed or as a contact
 * card's messaging address gives it (xmpp:name@server), lowercased as chat
 * keeps it; null for anything else.
 */
fun xmppAddress(value: String): String? {
    val text = value.trim()
    val address = if (text.lowercase().startsWith("xmpp:")) text.substring("xmpp:".length) else text
    return address.takeIf { XMPP_ADDRESS.matches(it) }?.lowercase()
}

/**
 * The person from Matrix or XMPP a member search names, by the id the chat
 * keeps them under and the address to add, unless they are already a member.
 */
fun outsider(search: String, members: Collection<String>): String? {
    val matrix = matrixAddress(search)
    val xmpp = if (matrix == null) xmppAddress(search) else null
    val address = matrix ?: xmpp ?: return null
    val id = if (xmpp != null) "xmpp:$xmpp" else address
    return address.takeIf { id !in members }
}

/** The addresses typed for a conversation on another network, by spaces or commas. */
fun addressList(text: String): List<String> = text.split(Regex("[\\s,]+")).map { it.trim() }.filter { it.isNotEmpty() }

// --- Encryption ---

/** The device's fingerprint in one version of OMEMO. */
data class EncryptionFingerprint(val version: String = "", val fingerprint: String = "")

/**
 * The server's device for the account. Matrix: its fingerprint, whether the
 * account has cross-signing keys, whether this device is verified, and
 * whether a recovery key and a key backup are set up. XMPP: its fingerprint
 * in each version of OMEMO.
 */
data class EncryptionStatus(
    val network: String = "",
    val address: String = "",
    val device: String = "",
    val fingerprint: String = "",
    val signing: Boolean = false,
    val verified: Boolean = false,
    val recovery: Boolean = false,
    val backup: Boolean = false,
    val fingerprints: List<EncryptionFingerprint> = emptyList(),
)

/** One of the conversation's people on its network. */
data class EncryptionPerson(val id: String = "", val name: String = "")

/** The account's device and the conversation's people. */
data class EncryptionOverview(
    val account: String = "",
    val status: EncryptionStatus = EncryptionStatus(),
    val people: List<EncryptionPerson> = emptyList(),
)

/** A device of a person's. Matrix says whether it is verified; XMPP gives the user's word on it. */
data class EncryptionDevice(
    val id: String = "",
    val name: String = "",
    val version: String = "",
    val fingerprint: String = "",
    val verified: Boolean = false,
    val trust: String = "",
    val own: Boolean = false,
)

/** The user's word on a device, as it shows: verified, distrusted or undecided. */
fun EncryptionDevice.trusted(): String = trust.ifEmpty { if (verified) "verified" else "undecided" }

/** An emoji verification under way on one of the user's Matrix accounts. */
data class Verification(
    val account: String = "",
    val flow: String = "",
    val person: String = "",
    val name: String = "",
    val device: String = "",
    /** Empty for a verification with the user's own other devices. */
    val room: String = "",
    /** requested, ready, compare, done or cancelled. */
    val phase: String = "",
    val started: Boolean = false,
    val emoji: List<String> = emptyList(),
    val reason: String = "",
    val updated: Long = 0,
) {
    val open: Boolean get() = phase != "done" && phase != "cancelled"
}

/** A fingerprint in groups of eight, as other clients show them. */
fun fingerprintGroups(fingerprint: String): String = fingerprint.chunked(8).joinToString(" ")

/** The verification to show: the latest one still under way that the user has not closed. */
fun verificationShown(verifications: List<Verification>, dismissed: Set<String>): Verification? =
    verifications.lastOrNull { it.flow !in dismissed && it.open }

/** The OMEMO version a fingerprint is in: OMEMO 2, or the original. */
const val OMEMO_CURRENT = "urn:xmpp:omemo:2"
