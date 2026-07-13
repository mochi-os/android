// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.model

/**
 * A pending friend invitation. The wire shape matches [Friend] — the server
 * uses the same record for received and sent invites; direction is implied
 * by which collection the invite appears in in the friends list response.
 */
typealias FriendInvite = Friend
