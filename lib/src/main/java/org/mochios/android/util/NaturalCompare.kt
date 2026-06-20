// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import java.text.Collator

/**
 * Case-insensitive, accent-insensitive, numeric-aware comparator for in-memory
 * sorts of user-facing strings. Mirrors `naturalCompare(a, b)` from
 * `@mochi/web` and is the Android counterpart for the rule "don't sort by
 * user-facing strings in SQL — sort in the consumer".
 *
 *  - "café" sorts equal to "cafe" (accent-insensitive)
 *  - "Über" sorts equal to "uber" (case-insensitive)
 *  - "Sprint 2" sorts before "Sprint 10" (numeric-aware: digit runs compare
 *    by numeric value, not lexicographically)
 *  - Locale-undefined: uses [Collator] with [Collator.PRIMARY] strength, so
 *    behaviour is consistent regardless of viewer language.
 *
 * Use this anywhere code currently uses `compareBy(String.CASE_INSENSITIVE_ORDER)`
 * for a user-visible name/title/label sort. For mixed comparators, chain via
 * `compareBy(naturalCompare) { it.name }` or `.thenBy(NaturalCompare) { it.name }`.
 */
object NaturalCompare : Comparator<String> {
    // PRIMARY strength compares only the base letter (a == A, é == e, ü == u).
    // Reused across calls — Collator is heavyweight to construct.
    private val collator: Collator = Collator.getInstance().apply {
        strength = Collator.PRIMARY
    }

    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        val lenA = a.length
        val lenB = b.length
        while (i < lenA && j < lenB) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                // Both sides start a digit run — compare them as numbers.
                val (numA, endA) = readDigitRun(a, i)
                val (numB, endB) = readDigitRun(b, j)
                // Compare the numeric runs by length-stripped-of-leading-zeros
                // first (longer = larger), then lexicographically. This avoids
                // any 64-bit overflow on huge digit sequences.
                val sigA = numA.trimStart('0').ifEmpty { "0" }
                val sigB = numB.trimStart('0').ifEmpty { "0" }
                val cmp = when {
                    sigA.length != sigB.length -> sigA.length - sigB.length
                    else -> sigA.compareTo(sigB)
                }
                if (cmp != 0) return cmp.coerceIn(-1, 1)
                // Equal numerically; tie-break on leading-zero count so that
                // "02" and "2" sort stably rather than being merged.
                val lzA = numA.length - sigA.length
                val lzB = numB.length - sigB.length
                if (lzA != lzB) return (lzA - lzB).coerceIn(-1, 1)
                i = endA
                j = endB
            } else {
                // Compare a single text run (up to the next digit on either
                // side) using the collator. This keeps "Sprint " on both sides
                // collapsing to equal even when one happens to be "sprint ".
                val endA = nextDigit(a, i)
                val endB = nextDigit(b, j)
                val textA = a.substring(i, endA)
                val textB = b.substring(j, endB)
                val cmp = collator.compare(textA, textB)
                if (cmp != 0) return cmp.coerceIn(-1, 1)
                i = endA
                j = endB
            }
        }
        return (lenA - i).compareTo(lenB - j).coerceIn(-1, 1)
    }

    private fun readDigitRun(s: String, start: Int): Pair<String, Int> {
        var end = start
        while (end < s.length && s[end].isDigit()) end++
        return s.substring(start, end) to end
    }

    private fun nextDigit(s: String, start: Int): Int {
        var i = start
        while (i < s.length && !s[i].isDigit()) i++
        return i
    }
}

/** Free-function alias matching the web export. */
fun naturalCompare(a: String, b: String): Int = NaturalCompare.compare(a, b)
