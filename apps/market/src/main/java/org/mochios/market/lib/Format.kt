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
 * Market-specific formatting helpers.
 *
 * Money/fingerprint helpers live in `org.mochios.android.format.MoneyFormat`
 * in the shared lib module — see the re-exports below for thin
 * [Currency]-enum bridges that keep existing call sites working unchanged.
 * Location parsing (Gson-driven, market-only payload shape) stays here.
 *
 * Mirrors `apps/market/web/src/lib/format.ts`.
 */

/** Decimal places for a given currency. Matches CURRENCIES_DATA on the web. */
fun currencyDecimals(currency: Currency): Int = libCurrencyDecimals(currency.name)

/**
 * Format a minor-unit amount as a localised currency string ("£12.34" /
 * "¥1234"). Bridges the market [Currency] enum to the lib helper, which
 * takes a free-form ISO 4217 string.
 */
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
