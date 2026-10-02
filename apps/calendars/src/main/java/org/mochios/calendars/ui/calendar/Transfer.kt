// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.calendar

import org.mochios.calendars.model.ImportResponse
import java.io.File

/** The types an iCalendar file goes by. */
object Icalendar {
    /** Its own type, which an export is saved as and an import uploaded as. */
    const val TYPE = "text/calendar"

    /**
     * The types the import's file picker offers. Many providers call an
     * `.ics` file something other than its own type, so the usual mislabels
     * are offered beside it.
     */
    val ACCEPTED = arrayOf(TYPE, "text/x-vcalendar", "application/ics", "text/plain", "application/octet-stream")
}

/**
 * Where an import into [calendar], named [name], stands: [done] of the file's
 * [total] objects read, and what became of them, summed over the rounds so
 * far. [total] is 0 until the first round answers.
 */
data class Tally(
    val calendar: String = "",
    val name: String = "",
    val done: Int = 0,
    val total: Int = 0,
    val imported: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val finished: Boolean = false,
)

/**
 * Imports [file] round by round. The first round uploads it; each one after
 * sends no file, only the staged id the first answered and the offset the
 * last reached, until the server says the import has finished. [round] is one
 * call of `-/calendars/import`; [progress] hears the tally after each round.
 * A round that does not move on would repeat forever, so it is refused.
 */
suspend fun rounds(
    file: File,
    start: Tally,
    round: suspend (file: File?, staged: String?, offset: Int) -> ImportResponse,
    progress: (Tally) -> Unit = {},
): Tally {
    var answer = round(file, null, 0)
    var tally = start
    while (true) {
        tally = tally.copy(
            done = answer.offset,
            total = answer.total,
            imported = tally.imported + answer.imported,
            skipped = tally.skipped + answer.skipped,
            failed = tally.failed + answer.failed,
            finished = answer.finished,
        )
        progress(tally)
        if (answer.finished) return tally
        val reached = answer.offset
        answer = round(null, answer.import, reached)
        check(answer.finished || answer.offset > reached) { "import stalled at $reached" }
    }
}
