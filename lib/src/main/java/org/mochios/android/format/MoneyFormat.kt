// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.format

import java.text.NumberFormat
import java.util.Currency as JavaCurrency
import java.util.Locale

/**
 * Money and ID formatting shared across apps. Amounts are always in minor
 * units, and the currency is a free-form ISO 4217 string (`"gbp"`, `"jpy"`),
 * since staff screens mix currencies straight from the server value.
 */

fun currencyDecimals(currencyCode: String): Int = when (currencyCode.trim().uppercase()) {
    "JPY", "KRW" -> 0
    else -> 2
}

/**
 * A minor-unit amount as a localised currency string; unknown [currencyCode]s
 * fall back to a plain numeric rendering.
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
 * Major-unit text to minor units; falls back to the locale parser for "12,34",
 * and invalid input becomes 0.
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
