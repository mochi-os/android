// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.util

// A leading one of these makes a spreadsheet evaluate the cell as a formula
// (=HYPERLINK(...), +cmd|...), so a field value is prefixed with an apostrophe
// before it can be interpreted. Tab and carriage return are the same trick
// once the cell is split.
private val FORMULA_LEADS = setOf('=', '+', '-', '@', '\t', '\r')

/**
 * One CSV cell: formula-leading values are defused with a leading apostrophe,
 * and a value holding a comma, quote or newline is quoted.
 */
fun csvCell(value: String): String {
    val safe = if (value.isNotEmpty() && value[0] in FORMULA_LEADS) "'$value" else value
    if (safe.none { char -> char == ',' || char == '"' || char == '\n' || char == '\r' }) {
        return safe
    }
    return "\"${safe.replace("\"", "\"\"")}\""
}
