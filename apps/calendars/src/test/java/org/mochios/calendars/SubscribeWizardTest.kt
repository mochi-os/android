// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.calendars.model.CalendarAccount
import org.mochios.calendars.ui.dialogs.SubscribeKind
import org.mochios.calendars.ui.dialogs.SubscribeStage
import org.mochios.calendars.ui.dialogs.accountType
import org.mochios.calendars.ui.dialogs.kindsOffered
import org.mochios.calendars.ui.dialogs.kindsSorted
import org.mochios.calendars.ui.dialogs.matching
import org.mochios.calendars.ui.dialogs.previous

/**
 * The subscribe wizard's three stages: what each kind is reached through,
 * which accounts a kind offers, and where a step back lands.
 */
class SubscribeWizardTest {

    /**
     * Google is offered when the server can grant an account, when the user
     * holds one already, or to an administrator, who can enable it; anyone
     * else would find nothing behind it.
     */
    @Test
    fun `google is offered only when something can come of it`() {
        val google = CalendarAccount(id = "g", type = "google", granted = listOf("login"))
        val everything = SubscribeKind.entries.toList()
        assertEquals(everything, kindsOffered(emptyList(), listOf("google"), administrator = false))
        assertEquals(everything, kindsOffered(listOf(google), emptyList(), administrator = false))
        assertEquals(everything, kindsOffered(emptyList(), emptyList(), administrator = true))
        assertEquals(
            listOf(SubscribeKind.APPLE, SubscribeKind.SERVER, SubscribeKind.ADDRESS),
            kindsOffered(emptyList(), emptyList(), administrator = false),
        )
    }

    /** The first stage lists the kinds by their names, so the order follows the language. */
    @Test
    fun `the wizard offers the four kinds sorted by name`() {
        val english = mapOf(
            SubscribeKind.GOOGLE to "Google Calendar",
            SubscribeKind.APPLE to "Apple iCloud",
            SubscribeKind.SERVER to "Another Mochi or CalDAV server",
            SubscribeKind.ADDRESS to "Published calendar address (read-only)",
        )
        assertEquals(
            listOf(SubscribeKind.SERVER, SubscribeKind.APPLE, SubscribeKind.GOOGLE, SubscribeKind.ADDRESS),
            kindsSorted { english.getValue(it) },
        )
        // A language that names them differently lists them differently.
        val german = english + (SubscribeKind.ADDRESS to "Veröffentlichte Kalenderadresse") + (SubscribeKind.SERVER to "Ein anderer Mochi- oder CalDAV-Server")
        assertEquals(
            listOf(SubscribeKind.APPLE, SubscribeKind.SERVER, SubscribeKind.GOOGLE, SubscribeKind.ADDRESS),
            kindsSorted { german.getValue(it) },
        )
    }

    /**
     * Three kinds go through a connected account of their own type; the
     * published address goes through none, which is what tells the second
     * stage to show the subscribe form instead of an account list.
     */
    @Test
    fun `each kind names the account type it is reached through`() {
        assertEquals(CalendarAccount.TYPE_GOOGLE, accountType(SubscribeKind.GOOGLE))
        assertEquals(CalendarAccount.TYPE_APPLE, accountType(SubscribeKind.APPLE))
        assertEquals(CalendarAccount.TYPE_CALDAV, accountType(SubscribeKind.SERVER))
        assertEquals("", accountType(SubscribeKind.ADDRESS))
    }

    /** The accounts action lists every type at once, so each stage filters. */
    @Test
    fun `only the kind's own accounts are offered`() {
        val accounts = listOf(
            CalendarAccount(id = "g", type = CalendarAccount.TYPE_GOOGLE),
            CalendarAccount(id = "a", type = CalendarAccount.TYPE_APPLE),
            CalendarAccount(id = "c", type = CalendarAccount.TYPE_CALDAV),
            CalendarAccount(id = "d", type = CalendarAccount.TYPE_CALDAV),
        )
        assertEquals(listOf("g"), matching(accounts, SubscribeKind.GOOGLE).map { it.id })
        assertEquals(listOf("a"), matching(accounts, SubscribeKind.APPLE).map { it.id })
        assertEquals(listOf("c", "d"), matching(accounts, SubscribeKind.SERVER).map { it.id })
        assertTrue(matching(accounts, SubscribeKind.ADDRESS).isEmpty())
    }

    /** Back steps one stage, and the first stage has nowhere left to go. */
    @Test
    fun `back steps one stage and stops at the first`() {
        assertEquals(SubscribeStage.CREDENTIAL, previous(SubscribeStage.CALENDAR))
        assertEquals(SubscribeStage.KIND, previous(SubscribeStage.CREDENTIAL))
        assertNull(previous(SubscribeStage.KIND))
    }
}
