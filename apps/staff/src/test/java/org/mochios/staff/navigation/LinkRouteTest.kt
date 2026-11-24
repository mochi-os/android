// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A link a staff notification carries opens the queue it names, over the
 * dashboard; a link to the console itself, or to a section it has no screen
 * for, opens the dashboard alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LinkRouteTest {

    /** The section of a `/staff/<section>` link, as the app's router splits it. */
    private fun section(link: String): String? = link.trim('/').split('/').getOrNull(1)

    @Test
    fun `each queue a notification names opens its screen`() {
        assertEquals(StaffApp.DISPUTES, StaffApp.linkRoute(section("/staff/disputes")))
        assertEquals(StaffApp.REPORTS, StaffApp.linkRoute(section("/staff/reports")))
        assertEquals(StaffApp.LISTINGS, StaffApp.linkRoute(section("/staff/listings")))
        assertEquals(StaffApp.APPEALS, StaffApp.linkRoute(section("/staff/appeals")))
        assertEquals(StaffApp.ACCOUNTS, StaffApp.linkRoute(section("/staff/accounts")))
    }

    @Test
    fun `the console itself opens the dashboard alone`() {
        assertNull(StaffApp.linkRoute(section("/staff/")))
        assertNull(StaffApp.linkRoute(section("/staff")))
        assertNull(StaffApp.linkRoute(""))
    }

    @Test
    fun `every drawer screen can be linked to, and nothing else`() {
        for (route in listOf(
            StaffApp.LISTINGS, StaffApp.APPEALS, StaffApp.REPORTS, StaffApp.DISPUTES, StaffApp.REVIEWS,
            StaffApp.MODERATION, StaffApp.CATEGORIES, StaffApp.ACCOUNTS, StaffApp.TEAM, StaffApp.CONFIG,
        )) {
            assertEquals(route, StaffApp.linkRoute(route.removePrefix("staff/")))
        }
        assertNull(StaffApp.linkRoute("stripe"))
        assertNull(StaffApp.linkRoute("team/add"))
    }

    @Test
    fun `a listing's view and its moderation log are routes of their own`() {
        assertEquals("staff/listings/view/l1", StaffApp.listing("l1"))
        assertNull(StaffApp.listing("a/b"))
        assertEquals("staff/moderation?listing=l1", StaffApp.moderation("l1"))
    }
}
