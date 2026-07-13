// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.history

/** One line of a line-level diff. */
enum class DiffType { UNCHANGED, ADDED, REMOVED }

data class DiffLine(val type: DiffType, val text: String)

/**
 * Line-level diff of [oldText] against [newText], the Kotlin analogue of the
 * web revision view's `diffLines` from the `diff` package. Uses a longest-
 * common-subsequence walk so unchanged lines are preserved and only genuine
 * insertions/removals are marked. Wiki pages are small, so the O(n*m) table is
 * fine.
 *
 * A trailing newline is dropped before splitting (as web does) so a page that
 * ends in "\n" doesn't produce a spurious blank trailing line.
 */
fun diffLines(oldText: String, newText: String): List<DiffLine> {
    val a = oldText.removeSuffix("\n").split("\n")
    val b = newText.removeSuffix("\n").split("\n")
    val n = a.size
    val m = b.size

    // lcs[i][j] = length of the LCS of a[i:] and b[j:].
    val lcs = Array(n + 1) { IntArray(m + 1) }
    for (i in n - 1 downTo 0) {
        for (j in m - 1 downTo 0) {
            lcs[i][j] = if (a[i] == b[j]) {
                lcs[i + 1][j + 1] + 1
            } else {
                maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }
    }

    val out = ArrayList<DiffLine>(n + m)
    var i = 0
    var j = 0
    while (i < n && j < m) {
        when {
            a[i] == b[j] -> { out.add(DiffLine(DiffType.UNCHANGED, a[i])); i++; j++ }
            lcs[i + 1][j] >= lcs[i][j + 1] -> { out.add(DiffLine(DiffType.REMOVED, a[i])); i++ }
            else -> { out.add(DiffLine(DiffType.ADDED, b[j])); j++ }
        }
    }
    while (i < n) { out.add(DiffLine(DiffType.REMOVED, a[i])); i++ }
    while (j < m) { out.add(DiffLine(DiffType.ADDED, b[j])); j++ }
    return out
}
