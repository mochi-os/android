// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.model

/**
 * JSON body of `POST -/subscribe`.
 *
 * @property target Entity id or fingerprint of the wiki to subscribe to.
 * @property server Home-server hint from the directory hit. Omitted from the
 *   payload when null, which is what the two-pass subscribe retry (first with
 *   the hint, again without it on a 502) relies on for its second attempt.
 */
data class SubscribeRequest(
    val target: String,
    val server: String? = null,
)
