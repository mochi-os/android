// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.Flights
import org.mochios.android.i18n.Maps
import org.mochios.android.i18n.UserPreferences
import org.mochios.calendars.ui.calendar.geo
import org.mochios.calendars.ui.calendar.openLocation
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Where an event's location opens, from the event sheet or beside the editor's
 * field: a place in the phone's map app, a flight on the user's tracker.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocationTest {

    /** The phone as openLocation sees it: [claims] says whether an installed app takes an intent. */
    private class Phone(private val claims: (Intent) -> Boolean) :
        ContextWrapper(RuntimeEnvironment.getApplication()) {
        val started = mutableListOf<Intent>()
        override fun startActivity(intent: Intent) = startActivity(intent, null)
        override fun startActivity(intent: Intent, options: Bundle?) {
            if (!claims(intent)) throw ActivityNotFoundException()
            started += intent
        }
    }

    private val noApp = { intent: Intent -> intent.data?.scheme == "https" && !nonBrowser(intent) }

    private fun nonBrowser(intent: Intent) = intent.flags and Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER != 0

    private fun tab(intent: Intent) = intent.hasExtra(CustomTabsIntent.EXTRA_SESSION)

    @Test
    fun `a location becomes a geo search`() {
        assertEquals("geo:0,0?q=Meeting%20room", geo("Meeting room"))
    }

    @Test
    fun `the location is trimmed and its punctuation encoded`() {
        assertEquals(
            "geo:0,0?q=12%20Example%20St%20%26%20Co%2C%20Springfield",
            geo("  12 Example St & Co, Springfield "),
        )
    }

    @Test
    fun `a place opens in the phone's map app`() {
        val phone = Phone { true }
        openLocation(phone, "Meeting room", UserPreferences())
        assertEquals(listOf("geo:0,0?q=Meeting%20room"), phone.started.map { it.dataString })
        assertEquals(Intent.ACTION_VIEW, phone.started.single().action)
    }

    @Test
    fun `with no map app a place is searched on the user's map service in a tab`() {
        val phone = Phone { intent -> intent.data?.scheme != "geo" }
        openLocation(phone, "Meeting room", UserPreferences(maps = Maps.GOOGLE))
        val opened = phone.started.single()
        assertEquals("https://www.google.com/maps/search/?api=1&query=Meeting%20room", opened.dataString)
        assertTrue(tab(opened))
    }

    @Test
    fun `a flight opens in the tracker's own app when one claims the link`() {
        val phone = Phone { true }
        openLocation(phone, "Aer Lingus EI 59", UserPreferences(flights = Flights.FLIGHTAWARE))
        val opened = phone.started.single()
        assertEquals("https://www.flightaware.com/live/flight/EI59", opened.dataString)
        assertTrue(nonBrowser(opened))
        assertTrue(opened.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertFalse(tab(opened))
    }

    @Test
    fun `with no tracker app the flight opens on the tracker's site in a tab`() {
        val phone = Phone(noApp)
        openLocation(phone, "EI59", UserPreferences())
        val opened = phone.started.single()
        assertEquals("https://www.flightradar24.com/data/flights/ei59", opened.dataString)
        assertTrue(tab(opened))
    }

    @Test
    @Config(sdk = [29])
    fun `before Android 11 a flight opens straight in a tab, which cannot tell a browser from an app`() {
        val phone = Phone { true }
        openLocation(phone, "EI59", UserPreferences())
        val opened = phone.started.single()
        assertFalse(nonBrowser(opened))
        assertTrue(tab(opened))
    }
}
