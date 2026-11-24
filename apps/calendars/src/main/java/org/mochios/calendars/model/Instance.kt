// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.model

import com.google.gson.annotations.SerializedName

/**
 * One occurrence of an event inside a range, as `-/events` returns it. The
 * server expands recurrences itself, so every view renders from this shape.
 *
 * [start] and [finish] are epoch seconds. [date] is present on an all-day
 * occurrence only, as `YYYY-MM-DD`, and is what a view should place it by:
 * an all-day occurrence's [start] is the midnight of that date in the user's
 * own timezone, not in the viewer's. [exception] marks an occurrence the user
 * has detached from its series. [zone] is the zone each end was written in,
 * on a timed occurrence only. [alarm] says the occurrence's own event carries
 * a reminder; a changed occurrence answers for itself, and a core that
 * predates the field leaves it out. [status] is the event's `STATUS` as
 * written, blank when it has none. [colour] is the colour the occurrence is
 * drawn in: its event's own `COLOR` when it carries one, else its calendar's.
 * [replaced] is, on an exception, the start of the occurrence it replaces,
 * given as [start] is: an override is listed at its own start, which "This
 * event" may have moved. A core that predates the field leaves it out.
 */
data class Instance(
    val event: String = "",
    val calendar: String = "",
    val colour: String = "",
    val readonly: Boolean = false,
    val uid: String = "",
    val component: String = "",
    val summary: String = "",
    val location: String = "",
    val description: String = "",
    val status: String = "",
    val start: Long = 0,
    val finish: Long = 0,
    val allday: Boolean = false,
    val date: String? = null,
    val recurring: Boolean = false,
    val exception: Boolean = false,
    @SerializedName("occurrence") val replaced: Long? = null,
    val zone: Zone? = null,
    val alarm: Boolean = false,
    /**
     * Never sent by the server: on an occurrence a move of a series, whole or
     * from one occurrence on, draws read-only while it is written, what it is
     * known by in place of its event ([identity]), so it stands apart from
     * the occurrences the move leaves, such as those before a cut, which may
     * hold the place it moves onto.
     */
    @Transient val mark: String? = null,
) {
    /** The occurrence has been called off: its event's `STATUS:CANCELLED`. */
    val cancelled: Boolean get() = status.trim().equals("CANCELLED", ignoreCase = true)

    /** The occurrence is not yet certain: its event's `STATUS:TENTATIVE`. */
    val tentative: Boolean get() = status.trim().equals("TENTATIVE", ignoreCase = true)

    /** The occurrence has no title of its own, so the views say "(No title)". */
    val untitled: Boolean get() = summary.isBlank()

    /** A birthday occurrence is derived from a contact and has no event to open. */
    val birthday: Boolean get() = event.startsWith(BIRTHDAY)

    /** Whether a tap opens the editor; a read-only occurrence opens the summary sheet instead. */
    val editable: Boolean get() = !readonly && !birthday

    /**
     * The occurrence of its series this is, as the listing gives a start:
     * the one an override replaces, else its own [start]. The editor and a
     * drag find its override by it; a core that does not name the occurrence
     * an override replaces gives the override's own start, and [moved] says
     * so.
     */
    val occurrence: Long get() = replaced ?: start

    /**
     * What the views know the occurrence by: its event and [occurrence],
     * which no other occurrence listed shares. Its start is not enough:
     * "This event" may have moved an override onto another occurrence's. An
     * occurrence a move draws read-only is known by its [mark] in place of
     * its event.
     */
    val identity: Pair<String, Long> get() = (mark ?: event) to occurrence

    /**
     * An override listed by a core that does not name the occurrence it
     * replaces: its [occurrence] is where it now falls, and where it was moved
     * from is read from its event's tree.
     */
    val moved: Boolean get() = exception && replaced == null

    companion object {
        const val BIRTHDAY = "birthday-"
    }
}

/** A colour as the server writes one. */
private val HEX = Regex("^#[0-9a-fA-F]{6}$")

/**
 * The colour an occurrence is drawn in: [own], the colour it arrived with,
 * when that is a `#rrggbb` colour, and otherwise [calendar], its calendar's.
 */
fun tint(own: String?, calendar: String?): String =
    own?.trim()?.takeIf { HEX.matches(it) } ?: calendar.orEmpty()

/**
 * The IANA zone each end of a timed occurrence was written in, its `TZID`:
 * a flight can start in one and end in another. Blank means UTC or floating,
 * which is read in the user's own zone.
 */
data class Zone(
    val start: String = "",
    val finish: String = "",
)

/**
 * The body of `-/events`. [truncated] means the range held more occurrences
 * than the server will list, so the view is showing part of it.
 */
data class InstancesResponse(
    val instances: List<Instance> = emptyList(),
    val truncated: Boolean = false,
)
