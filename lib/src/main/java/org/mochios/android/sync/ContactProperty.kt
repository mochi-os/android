// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.sync

/**
 * One vCard property of a contact's card: the property [name] (a protocol
 * token such as `FN` or `EMAIL`, exempt from the single-word rule), its
 * parameters — `TYPE` chiefly — and its [value]. Structured values keep the
 * vCard component order, `;` separated: `N` is
 * family;given;additional;prefix;suffix and `ADR` is
 * pobox;extended;street;city;region;postcode;country.
 *
 * The people app's editor and the contacts sync adapter share this shape; the
 * server stores the card as a list of these.
 */
data class ContactProperty(
    val name: String = "",
    val params: Map<String, List<String>> = emptyMap(),
    val value: String = "",
)

/**
 * Split a structured vCard value on its unescaped `;` separators, undoing the
 * escapes each component carries.
 */
fun splitComponents(value: String): List<String> {
    val out = mutableListOf<String>()
    val current = StringBuilder()
    var escaped = false
    for (character in value) {
        when {
            escaped -> {
                current.append(if (character == 'n' || character == 'N') '\n' else character)
                escaped = false
            }
            character == '\\' -> escaped = true
            character == ';' -> {
                out.add(current.toString())
                current.setLength(0)
            }
            else -> current.append(character)
        }
    }
    if (escaped) current.append('\\')
    out.add(current.toString())
    return out
}

/** Join components back into one structured value, escaping as vCard wants. */
fun joinComponents(components: List<String>): String =
    components.joinToString(";") { component ->
        buildString {
            for (character in component) {
                when (character) {
                    '\\' -> append("\\\\")
                    ';' -> append("\\;")
                    '\n' -> append("\\n")
                    else -> append(character)
                }
            }
        }
    }
