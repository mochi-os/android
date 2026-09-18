// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.model

import com.google.gson.annotations.SerializedName

/**
 * An address book: a `book` entity holding contacts. [count] is how many
 * contacts it holds and [version] its change token. The first book an identity
 * gets is its [isDefault] one, which can be renamed but not deleted.
 */
data class Book(
    val id: String = "",
    val fingerprint: String = "",
    val name: String = "",
    val count: Int = 0,
    // The wire name is the Kotlin keyword `default`, so the field is spelled
    // out and mapped rather than back-ticked.
    @SerializedName("default")
    val isDefault: Boolean = false,
    val version: Long = 0,
    val created: Long = 0,
    val updated: Long = 0,
)
