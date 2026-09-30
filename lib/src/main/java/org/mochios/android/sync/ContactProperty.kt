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
 * server stores the card as a list of these. [group] is the vCard group tying
 * a property to its siblings, as `item1.EMAIL` to the `item1.X-ABLabel` that
 * names it; null for none.
 */
data class ContactProperty(
    val name: String = "",
    val params: Map<String, List<String>> = emptyMap(),
    val value: String = "",
    val group: String? = null,
)

/**
 * Split a structured vCard value on its unescaped `;` separators. Besides
 * `\;`, the escapes this client used to write (`\,`, `\n`, `\\`) are undone;
 * any other backslash is the text's own and stays.
 */
fun splitComponents(value: String): List<String> {
    val out = mutableListOf<String>()
    val current = StringBuilder()
    var escaped = false
    for (character in value) {
        when {
            escaped -> {
                when (character) {
                    'n', 'N' -> current.append('\n')
                    ';', ',', '\\' -> current.append(character)
                    else -> current.append('\\').append(character)
                }
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

/**
 * Join components back into one structured value in the form the server
 * stores, which is what its vCard parser gives: only a `;` inside a component
 * is escaped. Commas, newlines and backslashes stay plain, since the server
 * escapes them itself when it writes the card for a client; escaping them here
 * too reached other clients as a literal `\n`.
 */
fun joinComponents(components: List<String>): String =
    components.joinToString(";") { it.replace(";", "\\;") }
