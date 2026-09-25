// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.calendars.model.Calendar
import org.mochios.calendars.model.CalendarAccount

/**
 * What a calendar's kind says about it. A linked calendar mirrors a
 * collection on another server and is written to as the user's own is, so it
 * is neither a subscription nor read-only; what it shares with a subscription
 * is that the server fetches it, which is the one menu entry they have in
 * common.
 */
class CalendarKindTest {

    private fun calendar(kind: String, readonly: Boolean) =
        Calendar(id = "c", name = "Work", kind = kind, readonly = readonly)

    @Test
    fun `a linked calendar is linked and nothing else`() {
        val linked = calendar(Calendar.KIND_LINKED, readonly = false)
        assertTrue(linked.linked)
        assertFalse(linked.subscription)
        assertFalse(linked.birthdays)
    }

    @Test
    fun `a calendar of the user's own is not linked`() {
        assertFalse(calendar(Calendar.KIND_OWN, readonly = false).linked)
        assertFalse(calendar(Calendar.KIND_SUBSCRIPTION, readonly = true).linked)
        assertFalse(calendar(Calendar.KIND_BIRTHDAYS, readonly = true).linked)
    }

    /**
     * The server says outright whether a calendar takes writes, and a linked
     * one does. The editor reads that rather than the kind, so a linked
     * calendar reaches it.
     */
    @Test
    fun `a linked calendar takes writes and a subscription does not`() {
        assertFalse(calendar(Calendar.KIND_LINKED, readonly = false).readonly)
        assertTrue(calendar(Calendar.KIND_SUBSCRIPTION, readonly = true).readonly)
    }

    /** Both the subscription and the linked calendar carry the fetch entry. */
    @Test
    fun `only a fetched calendar offers to fetch now`() {
        fun fetches(calendar: Calendar) = calendar.subscription || calendar.linked
        assertTrue(fetches(calendar(Calendar.KIND_LINKED, readonly = false)))
        assertTrue(fetches(calendar(Calendar.KIND_SUBSCRIPTION, readonly = true)))
        assertFalse(fetches(calendar(Calendar.KIND_OWN, readonly = false)))
        assertFalse(fetches(calendar(Calendar.KIND_BIRTHDAYS, readonly = true)))
    }

    /**
     * A Google account linked for sign-in alone holds no calendar access, so
     * the link screen shows it but will not open it.
     */
    @Test
    fun `an account holds calendars only once calendar access is granted`() {
        val signin = CalendarAccount(id = "a", type = "google", granted = listOf("login"))
        val granted = CalendarAccount(id = "b", type = "google", granted = listOf("login", "calendar"))
        val apple = CalendarAccount(id = "c", type = "apple", granted = listOf("calendar"))
        assertFalse(signin.calendars)
        assertTrue(granted.calendars)
        assertTrue(apple.calendars)
    }
}
