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
)
