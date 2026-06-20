// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.lib

/**
 * Common shipping carriers offered as picker options when a seller marks an
 * order as shipped. Free-form: when none of these match, the dialog falls
 * back to a text input so the seller can type whatever they use.
 *
 * Mirrors the carrier list on `apps/market/web/src/features/sale/
 * ship-order-dialog.tsx`. The list is intentionally short — exhaustive
 * worldwide carrier coverage isn't useful, so we keep the most common
 * picks and let everyone else type theirs in.
 */
val COMMON_CARRIERS: List<String> = listOf(
    "DHL",
    "FedEx",
    "UPS",
    "USPS",
    "Royal Mail",
    "Hermes",
    "DPD",
    "Australia Post",
    "Canada Post",
    "Japan Post",
    "Yamato",
    "An Post",
    "PostNL",
    "Deutsche Post",
    "La Poste",
    "Correos",
)
