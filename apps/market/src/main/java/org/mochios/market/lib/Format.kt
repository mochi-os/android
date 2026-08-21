// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.lib

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import org.mochios.android.format.currencyDecimals as libCurrencyDecimals
import org.mochios.android.format.formatPrice as libFormatPrice
import org.mochios.android.format.toMinorUnits as libToMinorUnits
import org.mochios.android.model.PlaceData
import org.mochios.market.model.Currency
import java.util.Locale

/**
 * Market formatting helpers; mirrors `apps/market/web/src/lib/format.ts`. Money
 * helpers bridge to the shared lib `MoneyFormat`.
 */

/** Decimal places for a given currency. Matches CURRENCIES_DATA on the web. */
fun currencyDecimals(currency: Currency): Int = libCurrencyDecimals(currency.name)

fun formatPrice(
    amount: Long,
    currency: Currency,
    locale: Locale = Locale.getDefault(),
): String = libFormatPrice(amount, currency.name, locale)

/**
 * Convert a free-text major-unit input (e.g. "12.34") into minor units for
 * the given currency.
 */
fun toMinorUnits(majorString: String, currency: Currency): Long =
    libToMinorUnits(majorString, currency.name)

/** First 9 chars of an entity ID — re-exported from lib for source-compat. */
fun formatFingerprint(id: String): String =
    org.mochios.android.format.formatFingerprint(id)

/**
 * Aggregate seller ratings (`Listing.seller_rating`, `Account.rating`,
 * `AccountSummary.rating`) arrive as integer hundredths (`500` = 5.00);
 * individual `Review.rating` is already 0-5.
 */
fun ratingStars(hundredths: Double): Float = (hundredths / 100.0).toFloat()

/**
 * The server's `location` JSON blob: `name` at minimum, optionally `country` /
 * `region` / `lat` / `lon`.
 */
data class ParsedLocation(
    val name: String = "",
    val country: String = "",
    val region: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val category: String = "",
)

fun parseLocation(json: String): ParsedLocation? {
    if (json.isBlank()) return null
    return try {
        val parsed = Gson().fromJson(json, ParsedLocation::class.java)
        // Gson returns null for the JSON literal "null"; treat the same as blank.
        if (parsed == null || parsed.name.isBlank() && parsed.country.isBlank()) {
            ParsedLocation(name = json.trim())
        } else {
            parsed
        }
    } catch (_: JsonSyntaxException) {
        ParsedLocation(name = json.trim())
    } catch (_: Exception) {
        ParsedLocation(name = json.trim())
    }
}

fun locationName(parsed: ParsedLocation?): String {
    if (parsed == null) return ""
    val name = parsed.name.trim()
    val country = parsed.country.trim()
    return when {
        name.isNotEmpty() && country.isNotEmpty() && !name.equals(country, ignoreCase = true) ->
            "$name, $country"
        name.isNotEmpty() -> name
        country.isNotEmpty() -> country
        else -> ""
    }
}

fun ParsedLocation.toPlaceData(): PlaceData? {
    if (lat == 0.0 && lon == 0.0) return null
    return PlaceData(
        name = name,
        lat = lat,
        lon = lon,
        country = country,
        state = region,
        category = category,
    )
}
