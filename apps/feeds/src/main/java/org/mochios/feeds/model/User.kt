// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.model

/**
 * A person returned by the feeds backend's `users/search` proxy (which calls
 * people.users/search). `id` is a full entity id — used directly as the access
 * subject, matching web's `selectedUser.id`.
 */
data class User(
    val id: String = "",
    val name: String = "",
)
