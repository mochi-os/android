// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui

import androidx.lifecycle.SavedStateHandle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.staff.StaffServer
import org.mochios.staff.StaffServer.Companion.ok
import org.mochios.staff.model.StaffMember
import org.mochios.staff.ui.reviews.ReviewsViewModel
import org.mochios.staff.ui.team.TeamViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A row keeps its names after an action on it: the answer, a list row, takes
 * its place whole, and a server that answers the bare row changes only what
 * the action changed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ListShapedAnswerTest {

    @Volatile private var bare = false

    private val server = StaffServer { request ->
        val path = request.requestUrl!!.encodedPath
        when {
            path.endsWith("-/reviews/list") -> ok(
                """{"reviews": [{"id": "v1", "reviewer": "u1", "reviewer_name": "Ann", "subject": "u2", "subject_name": "Bob",
                   "listing_title": "Teapot", "status": "published"}], "total": 1, "next": ""}""",
            )
            path.endsWith("-/reviews/action") -> if (bare) {
                ok("""{"id": "v1", "reviewer": "u1", "subject": "u2", "status": "removed"}""")
            } else {
                ok("""{"id": "v1", "reviewer": "u1", "reviewer_name": "Ann Lee", "subject": "u2", "subject_name": "Bob",
                      "listing_title": "Blue teapot", "status": "removed"}""")
            }
            path.endsWith("-/team/list") -> ok(
                """[{"id": "m1", "name": "Cat", "fingerprint": "abc123def", "role": "support", "addedby": "m0", "addedby_name": "Dan"}]""",
            )
            path.endsWith("-/team/role") -> if (bare) {
                ok("""{"id": "m1", "role": "admin", "added": 5, "addedby": "m0"}""")
            } else {
                ok("""{"id": "m1", "name": "Cat", "fingerprint": "abc123def", "role": "admin", "addedby": "m0", "addedby_name": "Dana"}""")
            }
            else -> null
        }
    }

    @Before
    fun begin() {
        server.start()
    }

    @After
    fun end() = server.stop()

    private fun reviews(): ReviewsViewModel {
        val model = ReviewsViewModel(server.repository, SavedStateHandle())
        server.until { !model.state.value.isLoading }
        return model
    }

    private fun team(): TeamViewModel {
        val model = TeamViewModel(server.repository)
        server.until { !model.state.value.isLoading && model.state.value.members.isNotEmpty() }
        return model
    }

    @Test
    fun `a review action's list row takes the row's place`() {
        val model = reviews()
        model.runAction(model.state.value.reviews.single(), "remove")
        server.until { model.state.value.reviews.single().status == "removed" }
        assertEquals("Ann Lee", model.state.value.reviews.single().reviewerName)
        assertEquals("Blue teapot", model.state.value.reviews.single().listingTitle)
    }

    @Test
    fun `a bare review answer changes only the status`() {
        bare = true
        val model = reviews()
        model.runAction(model.state.value.reviews.single(), "remove")
        server.until { model.state.value.reviews.single().status == "removed" }
        val row = model.state.value.reviews.single()
        assertEquals("Ann", row.reviewerName)
        assertEquals("Bob", row.subjectName)
        assertEquals("Teapot", row.listingTitle)
    }

    @Test
    fun `a role change's list row takes the row's place`() {
        val model = team()
        model.changeRole(model.state.value.members.single(), "admin")
        server.until { model.state.value.members.single().role == "admin" }
        assertEquals("Dana", model.state.value.members.single().addedbyName)
    }

    @Test
    fun `a bare role answer changes only the role`() {
        bare = true
        val model = team()
        model.changeRole(model.state.value.members.single(), "admin")
        server.until { model.state.value.members.single().role == "admin" }
        val row: StaffMember = model.state.value.members.single()
        assertEquals("Cat", row.name)
        assertEquals("abc123def", row.fingerprint)
        assertEquals("Dan", row.addedbyName)
    }
}
