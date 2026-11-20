// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.lib

import android.content.res.Resources
import androidx.annotation.StringRes
import org.mochios.android.util.NaturalCompare
import org.mochios.market.R
import org.mochios.market.model.ShippingOption
import org.mochios.market.model.ShippingRegions
import java.text.Normalizer
import java.util.Locale

/**
 * Shipping regions are codes: a country's ISO 3166-1 alpha-2 code in upper
 * case ("GB"), or a group of countries in lower case ("europe"), whose members
 * the Comptroller lists in `shipping/regions`. A region stored before codes
 * (free text) is shown as it was written and never matched.
 */

/** The group that covers every country. */
const val WORLD = "world"

/** The region groups, in the order an editor offers them, with their names. */
private val GROUPS: List<Pair<String, Int>> = listOf(
    WORLD to R.string.market_region_world,
    "europe" to R.string.market_region_europe,
    "eu" to R.string.market_region_eu,
    "eea" to R.string.market_region_eea,
    "north_america" to R.string.market_region_north_america,
    "south_america" to R.string.market_region_south_america,
    "latin_america" to R.string.market_region_latin_america,
    "middle_east" to R.string.market_region_middle_east,
    "africa" to R.string.market_region_africa,
    "asia" to R.string.market_region_asia,
    "oceania" to R.string.market_region_oceania,
    "asia_pacific" to R.string.market_region_asia_pacific,
)

private val COUNTRY = Regex("^[A-Z]{2}$")

/** Whether [region] is a country code. */
fun isCountry(region: String): Boolean = COUNTRY.matches(region)

/** The locale naming country [code], a country code. */
private fun country(code: String): Locale = Locale.Builder().setRegion(code).build()

/** The name of group [region], or null when it is not a group this client knows. */
@StringRes
fun groupLabel(region: String): Int? = GROUPS.firstOrNull { (code, _) -> code == region }?.second

/**
 * [region] as a reader sees it: a country's name in [locale], a group's
 * translated name, or anything else (a region written before codes) as it is.
 */
fun regionName(region: String, resources: Resources, locale: Locale = Locale.getDefault()): String {
    if (isCountry(region)) {
        val name = country(region).getDisplayCountry(locale)
        return name.ifBlank { region }
    }
    val label = groupLabel(region) ?: return region
    return resources.getString(label)
}

/**
 * The countries a buyer picks from: the server's list, or the platform's while
 * the server's is not to hand, sorted by their names in [locale].
 */
fun countryChoices(regions: ShippingRegions?, locale: Locale = Locale.getDefault()): List<String> {
    val codes = regions?.countries?.filter { code -> isCountry(code) }?.takeIf { it.isNotEmpty() }
        ?: Locale.getISOCountries().toList()
    return codes.distinct().sortedWith { a, b ->
        NaturalCompare.compare(country(a).getDisplayCountry(locale), country(b).getDisplayCountry(locale))
    }
}

/**
 * What a seller ships to: the groups first, in their fixed order (only those
 * the server lists, once its list is to hand), then every country by name.
 */
fun regionChoices(regions: ShippingRegions?, locale: Locale = Locale.getDefault()): List<String> {
    val groups = GROUPS.map { (code, _) -> code }
        .filter { code -> regions == null || regions.groups.isEmpty() || code in regions.groups }
    return groups + countryChoices(regions, locale)
}

/**
 * Whether [region] covers [country] (a code): true or false when both are
 * codes and the group's members are known, null when it cannot be said - a
 * region written before codes, a group whose members are not to hand, or no
 * country chosen. `world` covers every country.
 */
fun covers(region: String, country: String, regions: ShippingRegions?): Boolean? {
    if (!isCountry(country)) return null
    if (isCountry(region)) return region == country
    if (region == WORLD) return true
    val members = regions?.groups?.get(region) ?: return null
    return country in members
}

/**
 * The options a buyer in [country] may choose: all but those whose region is
 * known not to cover it. All of them while no country is chosen.
 */
fun optionsFor(country: String, options: List<ShippingOption>, regions: ShippingRegions?): List<ShippingOption> =
    options.filter { option -> covers(option.region, country, regions) != false }

/** The cheapest option whose region covers [country], or null when none is known to. */
fun cheapestFor(country: String, options: List<ShippingOption>, regions: ShippingRegions?): ShippingOption? =
    options.filter { option -> covers(option.region, country, regions) == true }.minByOrNull { option -> option.price }

/**
 * Whether [name], [region]'s name as shown, holds [query] ignoring case and
 * accents, or [query] is the code itself, for a picker narrowed as the user
 * types.
 */
fun regionMatches(region: String, name: String, query: String): Boolean {
    val wanted = plain(query)
    if (wanted.isEmpty()) return true
    return plain(name).contains(wanted) || region.equals(query.trim(), ignoreCase = true)
}

private val MARKS = Regex("\\p{Mn}+")

private fun plain(text: String): String =
    MARKS.replace(Normalizer.normalize(text.trim(), Normalizer.Form.NFD), "").lowercase()
