// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.model

/**
 * A local account from `/-/users/search`; unlike [User], carries no
 * relationship status.
 */
data class LocalUser(
    val id: String = "",
    val name: String = ""
)
