// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.lib

/**
 * Carrier picker options for marking an order shipped; anything else is typed
 * free-form. Mirrors web's `ship-order-dialog.tsx`.
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
