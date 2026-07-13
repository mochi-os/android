// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.lib

import org.mochios.market.model.ShippingOption

/**
 * Kotlin port of `apps/market/web/src/lib/shipping.ts`. Free-text
 * region-name → buyer-country matching used by the checkout shipping
 * picker. Heuristic, not a contract — the UI keeps the dropdown editable
 * so a wrong guess can be corrected.
 */

private val CATCH_ALL = setOf("worldwide", "international", "global", "anywhere")

private val EU = setOf(
    "austria", "belgium", "bulgaria", "croatia", "cyprus", "czechia",
    "czech republic", "denmark", "estonia", "finland", "france", "germany",
    "greece", "hungary", "ireland", "italy", "latvia", "lithuania",
    "luxembourg", "malta", "netherlands", "poland", "portugal", "romania",
    "slovakia", "slovenia", "spain", "sweden",
)

private val EEA = EU + setOf("iceland", "liechtenstein", "norway")

private val EUROPE = EEA + setOf(
    "albania", "andorra", "belarus", "bosnia and herzegovina", "gibraltar",
    "guernsey", "isle of man", "jersey", "kosovo", "moldova", "monaco",
    "montenegro", "north macedonia", "russia", "san marino", "serbia",
    "switzerland", "turkey", "ukraine", "united kingdom", "vatican city",
)

private val NORTH_AMERICA = setOf("canada", "mexico", "united states", "usa")

private val SOUTH_AMERICA = setOf(
    "argentina", "bolivia", "brazil", "chile", "colombia", "ecuador",
    "french guiana", "guyana", "paraguay", "peru", "suriname", "uruguay",
    "venezuela",
)

private val LATIN_AMERICA = SOUTH_AMERICA + setOf(
    "belize", "costa rica", "cuba", "dominican republic", "el salvador",
    "guatemala", "haiti", "honduras", "mexico", "nicaragua", "panama",
    "puerto rico",
)

private val AMERICAS = NORTH_AMERICA + SOUTH_AMERICA + LATIN_AMERICA

private val ASIA = setOf(
    "afghanistan", "bangladesh", "bhutan", "brunei", "cambodia", "china",
    "india", "indonesia", "japan", "kazakhstan", "kyrgyzstan", "laos",
    "malaysia", "maldives", "mongolia", "myanmar", "nepal", "north korea",
    "pakistan", "philippines", "singapore", "south korea", "sri lanka",
    "taiwan", "tajikistan", "thailand", "timor-leste", "turkmenistan",
    "uzbekistan", "vietnam",
)

private val MIDDLE_EAST = setOf(
    "bahrain", "iran", "iraq", "israel", "jordan", "kuwait", "lebanon", "oman",
    "palestine", "qatar", "saudi arabia", "syria", "turkey",
    "united arab emirates", "uae", "yemen",
)

private val AFRICA = setOf(
    "algeria", "angola", "benin", "botswana", "burkina faso", "burundi",
    "cameroon", "cape verde", "central african republic", "chad", "comoros",
    "democratic republic of the congo", "djibouti", "egypt", "equatorial guinea",
    "eritrea", "eswatini", "ethiopia", "gabon", "gambia", "ghana", "guinea",
    "guinea-bissau", "ivory coast", "kenya", "lesotho", "liberia", "libya",
    "madagascar", "malawi", "mali", "mauritania", "mauritius", "morocco",
    "mozambique", "namibia", "niger", "nigeria", "rwanda", "senegal",
    "seychelles", "sierra leone", "somalia", "south africa", "south sudan",
    "sudan", "tanzania", "togo", "tunisia", "uganda", "zambia", "zimbabwe",
)

private val OCEANIA = setOf(
    "australia", "fiji", "kiribati", "marshall islands", "micronesia",
    "nauru", "new zealand", "palau", "papua new guinea", "samoa",
    "solomon islands", "tonga", "tuvalu", "vanuatu",
)

private val ASIA_PACIFIC = ASIA + OCEANIA

private val REGION_GROUPS: Map<String, Set<String>> = mapOf(
    "africa" to AFRICA,
    "americas" to AMERICAS,
    "apac" to ASIA_PACIFIC,
    "asia" to ASIA,
    "asia pacific" to ASIA_PACIFIC,
    "asia-pacific" to ASIA_PACIFIC,
    "eea" to EEA,
    "eu" to EU,
    "europe" to EUROPE,
    "european economic area" to EEA,
    "european union" to EU,
    "latin america" to LATIN_AMERICA,
    "middle east" to MIDDLE_EAST,
    "north america" to NORTH_AMERICA,
    "oceania" to OCEANIA,
    "south america" to SOUTH_AMERICA,
)

/** Synonyms — buyer-typed names canonicalised to the keys in REGION_GROUPS. */
private val COUNTRY_ALIASES: Map<String, String> = mapOf(
    "america" to "united states",
    "britain" to "united kingdom",
    "deutschland" to "germany",
    "england" to "united kingdom",
    "españa" to "spain",
    "espana" to "spain",
    "great britain" to "united kingdom",
    "holland" to "netherlands",
    "italia" to "italy",
    "northern ireland" to "united kingdom",
    "scotland" to "united kingdom",
    "uae" to "united arab emirates",
    "uk" to "united kingdom",
    "us" to "united states",
    "usa" to "united states",
    "wales" to "united kingdom",
)

private fun normalise(s: String): String = s.trim().lowercase()

private fun canonicalCountry(country: String): String {
    val c = normalise(country)
    return COUNTRY_ALIASES[c] ?: c
}

/**
 * True if the buyer's [country] is covered by the seller's free-text
 * [region]. Case-insensitive; matches exact country names, the catch-all
 * "worldwide" / "global" / etc., and the region-group lookup table
 * (Europe, EU, Asia, etc.).
 */
fun countryInRegion(country: String, region: String): Boolean {
    val c = canonicalCountry(country)
    val r = normalise(region)
    if (c.isEmpty() || r.isEmpty()) return false
    if (c == r) return true
    if (r in CATCH_ALL) return true
    return REGION_GROUPS[r]?.contains(c) == true
}

/**
 * Pick the cheapest [ShippingOption] whose region matches the buyer's
 * country, or `null` if none match. Convenience over [countryInRegion] so
 * the checkout summary can default-select without duplicating the
 * filter+sort logic in every screen.
 */
fun cheapestMatchingZone(
    country: String,
    zones: List<ShippingOption>,
): ShippingOption? =
    zones
        .filter { countryInRegion(country, it.region) }
        .minByOrNull { it.price }
