// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.model

/**
 * A user known on this server (the `/-/users/search` endpoint result shape).
 * Distinguished from [User] in that it carries no relationship status or
 * directory metadata — used for picking group members from local accounts.
 */
data class LocalUser(
    val id: String = "",
    val name: String = ""
)
