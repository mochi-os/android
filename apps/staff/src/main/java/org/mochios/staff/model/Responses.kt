// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.model

/**
 * Generic ack payload for endpoints that just return `{ok: true}` (delete /
 * approve / reject / remove / set-config / decide-appeal). The Comptroller
 * actually returns the updated row on most of these, but the api file maps
 * the lightweight ones to [OkResponse] when callers don't need the row.
 */
data class OkResponse(val ok: Boolean = true)
