package org.mochios.market.lib

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import org.mochios.android.model.PlaceData
import org.mochios.market.model.Currency
import java.text.NumberFormat
import java.util.Locale

/**
 * Kotlin port of `apps/market/web/src/lib/format.ts`. Pure utility — no
 * Composable scope. Mirrors the web helpers used across the market SPA:
 * currency decimals, minor-units conversion, fingerprint slicing, location
 * JSON parsing, etc.
 *
 * Money in this app is always in minor units (pence/cents). The display
 * helpers route through [NumberFormat.getCurrencyInstance] so the symbol,
 * separator, and decimal mark match the user's locale.
 */

/** Decimal places for a given currency. Matches CURRENCIES_DATA on the web. */
fun currencyDecimals(currency: Currency): Int = when (currency) {
    Currency.JPY -> 0
    Currency.GBP, Currency.USD, Currency.EUR -> 2
}

/**
 * Format a minor-unit amount as a localised currency string ("£12.34" /
 * "¥1234"). Uses `NumberFormat.getCurrencyInstance(locale)` with the
 * currency forced to the listing's chosen ISO code — the [Currency] enum's
 * name matches the ISO 4217 code on the wire (GBP, USD, EUR, JPY).
 */
fun formatPrice(
    amount: Long,
    currency: Currency,
    locale: Locale = Locale.getDefault(),
): String {
    val decimals = currencyDecimals(currency)
    val major = amount.toDouble() / pow10(decimals).toDouble()
    val nf = NumberFormat.getCurrencyInstance(locale)
    nf.currency = java.util.Currency.getInstance(currency.name)
    nf.minimumFractionDigits = decimals
    nf.maximumFractionDigits = decimals
    return nf.format(major)
}

/**
 * Convert a free-text major-unit input (e.g. "12.34") into minor units for
 * the given currency. Tolerant of stray whitespace and the user's decimal
 * mark — falls back to the locale-default parser when the canonical
 * `Double.parseDouble` fails. Invalid input becomes 0.
 */
fun toMinorUnits(majorString: String, currency: Currency): Long {
    val trimmed = majorString.trim()
    if (trimmed.isEmpty()) return 0L
    val factor = pow10(currencyDecimals(currency))
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

/**
 * Parsed location payload. The market server stores `location` as a JSON
 * blob with at minimum `name`, optionally `country` / `region` /
 * `lat` / `lon`. When the value isn't valid JSON we synthesise a
 * [ParsedLocation] with the raw string as `name`.
 */
data class ParsedLocation(
    val name: String = "",
    val country: String = "",
    val region: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val category: String = "",
)

/**
 * Best-effort parse of a location JSON blob. Returns `null` when the input
 * is blank; returns a synthesised wrapper around the raw string when the
 * input is non-empty but not valid JSON (matches the web fallback).
 */
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

/**
 * Display string for a parsed location:
 *  - `"City, Country"` when both are set;
 *  - just `name` when only that's populated;
 *  - country alone when name is empty but country is set.
 * Returns the empty string for `null`.
 */
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

private fun pow10(n: Int): Long {
    var v = 1L
    repeat(n) { v *= 10L }
    return v
}

/**
 * Convert a [ParsedLocation] to the shared [PlaceData] shape used by
 * `LocationMapView`. Returns `null` when the parsed location has no
 * lat/lon to anchor a pin on.
 */
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
