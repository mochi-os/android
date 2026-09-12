// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.format

import java.text.NumberFormat
import java.util.Currency as JavaCurrency
import java.util.Locale
import org.mochios.android.i18n.Format

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
    format: Format? = null,
): String {
    val iso = currencyCode.trim().uppercase()
    val decimals = currencyDecimals(iso)
    val major = amount.toDouble() / pow10(decimals).toDouble()
    // The digits come from the user's number-format preference when the caller
    // supplies one: the device locale is only a fallback, and the two disagree
    // for anyone whose preference is not their device's. The currency symbol
    // and its placement still come from the locale, which is what carries them.
    val digits = format?.formatNumber(major, decimals)
    return try {
        val nf = NumberFormat.getCurrencyInstance(locale)
        nf.currency = JavaCurrency.getInstance(iso)
        nf.minimumFractionDigits = decimals
        nf.maximumFractionDigits = decimals
        if (digits == null) nf.format(major) else substituteDigits(nf.format(major), major, decimals, digits)
    } catch (_: Exception) {
        // Unknown currency code — render the amount with the right decimals
        // and tag it with the raw code so callers still see something useful.
        if (digits != null) return "$digits $iso".trim()
        val nf = NumberFormat.getNumberInstance(locale)
        nf.minimumFractionDigits = decimals
        nf.maximumFractionDigits = decimals
        "${nf.format(major)} $iso".trim()
    }
}

/**
 * Swap the locale's rendering of the number inside [formatted] for [digits],
 * keeping the currency symbol and its position. Falls back to [formatted] when
 * the number cannot be located, so a surprising currency pattern degrades to
 * the locale's own output rather than to something wrong.
 */
private fun substituteDigits(
    formatted: String,
    major: Double,
    decimals: Int,
    digits: String,
): String {
    val plain = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
        isGroupingUsed = true
    }.format(major)
    if (formatted.contains(plain)) return formatted.replace(plain, digits)
    val ungrouped = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
        isGroupingUsed = false
    }.format(major)
    if (formatted.contains(ungrouped)) return formatted.replace(ungrouped, digits)
    return formatted
}

/**
 * Major-unit text to minor units. Digits around a single "." or "," are read
 * with that character as the decimal mark whatever the device locale uses,
 * because the amount masks admit either and the locale parser would read
 * "12,34" as 1234 in a period-decimal locale. Anything else (grouping
 * separators, spaces) goes through the locale parser. Invalid input becomes 0.
 */
fun toMinorUnits(majorString: String, currencyCode: String): Long {
    val trimmed = majorString.trim()
    if (trimmed.isEmpty()) return 0L
    val factor = pow10(currencyDecimals(currencyCode))
    val parsed = if (PLAIN_AMOUNT.matches(trimmed)) {
        trimmed.replace(',', '.').toDoubleOrNull() ?: 0.0
    } else {
        try {
            NumberFormat.getNumberInstance(Locale.getDefault()).parse(trimmed)?.toDouble() ?: 0.0
        } catch (_: Exception) {
            0.0
        }
    }
    return kotlin.math.round(parsed * factor).toLong()
}

private val PLAIN_AMOUNT = Regex("""^\d*[.,]?\d*$""")

/**
 * A minor-unit amount as plain major-unit text for an editable field: a "."
 * decimal mark and no grouping, so it round-trips through [toMinorUnits] in
 * every locale. Zero renders as "" so an unset price shows an empty field.
 */
fun minorToMajorText(amount: Long, currencyCode: String): String {
    if (amount == 0L) return ""
    val decimals = currencyDecimals(currencyCode)
    if (decimals == 0) return amount.toString()
    val factor = pow10(decimals)
    val magnitude = kotlin.math.abs(amount)
    val sign = if (amount < 0) "-" else ""
    return sign + (magnitude / factor).toString() + "." +
        (magnitude % factor).toString().padStart(decimals, '0')
}

/**
 * Punctuate a server-supplied fingerprint as xxx-xxx-xxx, as web does. Never
 * pass an entity id: a fingerprint is a hash of the id, not its first nine
 * characters. Empty for a blank or short value, so a caller shows nothing
 * rather than a fabricated identifier.
 */
fun formatFingerprint(fingerprint: String): String {
    if (fingerprint.length < 9) return ""
    val fp = fingerprint.substring(0, 9)
    return "${fp.substring(0, 3)}-${fp.substring(3, 6)}-${fp.substring(6, 9)}"
}

private fun pow10(n: Int): Long {
    var v = 1L
    repeat(n) { v *= 10L }
    return v
}
