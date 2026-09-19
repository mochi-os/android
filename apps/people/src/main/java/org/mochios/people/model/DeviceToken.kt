// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.model

/**
 * A connected device's credential as `-/token/list` returns it. [hash]
 * identifies it for deletion; the token itself is shown only when created.
 * [created] and [used] are epoch seconds, [used] 0 until the device first
 * signs in with it.
 */
data class DeviceToken(
    val hash: String = "",
    val name: String = "",
    val created: Long = 0,
    val used: Long = 0,
)
