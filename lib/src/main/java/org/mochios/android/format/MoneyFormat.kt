// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.format

import java.text.NumberFormat
import java.util.Currency as JavaCurrency
import java.util.Locale

/**
 * Shared, app-agnostic money and ID formatting helpers.
 *
 * Mirrors the helpers used across Mochi apps (market, staff, …): currency
 * decimals, minor-units conversion, fingerprint slicing. App-specific
 * helpers — e.g. market's location parsing — stay in their app module.
 *
 * Money values are always in minor units (pence/cents); the display
 * helpers route through [NumberFormat.getCurrencyInstance] so the symbol,
 * group separator, and decimal mark match the user's locale.
 *
 * The wire-currency parameter is the lowest-common-denominator: a free-form
 * ISO 4217 string (`"gbp"`, `"usd"`, `"eur"`, `"jpy"`, …). Apps that own a
 * stronger [Currency]-enum type pass `currency.name.lowercase()`; staff
 * dashboards that mix currencies on one screen pass the raw server string.
 */

/**
 * Decimal places for a given ISO 4217 currency code. Comparison is
 * case-insensitive, so `"gbp"`, `"GBP"`, and `" GBP "` all match. Unknown
 * codes default to 2 decimals.
 */
fun currencyDecimals(currencyCode: String): Int = when (currencyCode.trim().uppercase()) {
    "JPY", "KRW" -> 0
    else -> 2
}

/**
 * Format a minor-unit amount as a localised currency string ("£12.34" /
 * "¥1234"). [currencyCode] is the ISO 4217 code (case-insensitive); blank
 * or unknown codes fall back to a plain numeric rendering with the right
 * decimal precision so a future currency added on the server doesn't crash
 * the screen.
 */
fun formatPrice(
    amount: Long,
    currencyCode: String,
    locale: Locale = Locale.getDefault(),
): String {
    val iso = currencyCode.trim().uppercase()
    val decimals = currencyDecimals(iso)
    val major = amount.toDouble() / pow10(decimals).toDouble()
    return try {
        val nf = NumberFormat.getCurrencyInstance(locale)
        nf.currency = JavaCurrency.getInstance(iso)
        nf.minimumFractionDigits = decimals
        nf.maximumFractionDigits = decimals
        nf.format(major)
    } catch (_: Exception) {
        // Unknown currency code — render the amount with the right decimals
        // and tag it with the raw code so callers still see something useful.
        val nf = NumberFormat.getNumberInstance(locale)
        nf.minimumFractionDigits = decimals
        nf.maximumFractionDigits = decimals
        "${nf.format(major)} $iso".trim()
    }
}

/**
 * Convert a free-text major-unit input (e.g. "12.34") into minor units for
 * the given currency. Tolerant of stray whitespace and the user's decimal
 * mark — falls back to the locale-default parser when the canonical
 * `Double.parseDouble` fails. Invalid input becomes 0.
 */
fun toMinorUnits(majorString: String, currencyCode: String): Long {
    val trimmed = majorString.trim()
    if (trimmed.isEmpty()) return 0L
    val factor = pow10(currencyDecimals(currencyCode))
    val parsed = trimmed.toDoubleOrNull() ?: run {
        // Fall back to the locale parser to handle "12,34" in European locales.
        try {
            NumberFormat.getNumberInstance(Locale.getDefault()).parse(trimmed)?.toDouble() ?: 0.0
        } catch (_: Exception) {
            0.0
        }
    }
    return kotlin.math.round(parsed * factor).toLong()
}

/** First 9 chars of an entity ID — the standard Mochi fingerprint slice. */
fun formatFingerprint(id: String): String =
    if (id.length <= 9) id else id.substring(0, 9)

private fun pow10(n: Int): Long {
    var v = 1L
    repeat(n) { v *= 10L }
    return v
}
