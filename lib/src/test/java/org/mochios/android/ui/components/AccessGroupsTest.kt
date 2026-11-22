// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.android.i18n.AppContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The add-access dialog's group segment says there are no groups only once
 * they have loaded: a spinner while they load (and before they are asked
 * for), and why they could not with a retry.
 *
 * The segment is driven outside its dialog: the dialog's content (at HEAD as
 * now) never settles under Robolectric's clock, so a test inside it times out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class AccessGroupsTest {

    @get:Rule
    val rule = createComposeRule()

    private val friends = listOf(AccessCandidate("@g1", "Friends"))
    private val failure = MochiError.ServerError(500, "Groups are unavailable")

    @Before
    fun begin() {
        AppContext.set(RuntimeEnvironment.getApplication())
    }

    @Test
    fun `groups not yet in hand read as loading, and none only once a load found none`() {
        assertEquals(GroupsShown.Loading, groupsShown(emptyList(), loading = false, error = null, requested = false))
        assertEquals(GroupsShown.Loading, groupsShown(emptyList(), loading = true, error = null, requested = true))
        assertEquals(GroupsShown.None, groupsShown(emptyList(), loading = false, error = null, requested = true))
        assertEquals(GroupsShown.Failed, groupsShown(emptyList(), loading = false, error = failure, requested = true))
        assertEquals(GroupsShown.Failed, groupsShown(emptyList(), loading = false, error = failure, requested = false))
        assertEquals(GroupsShown.Offered, groupsShown(friends, loading = false, error = null, requested = true))
    }

    @Test
    fun `the group segment asks once, not again while the groups are on the way`() {
        assertTrue(loadsGroups(tab = 1, groups = emptyList(), loading = false))
        assertFalse(loadsGroups(tab = 1, groups = emptyList(), loading = true))
        assertFalse(loadsGroups(tab = 1, groups = friends, loading = false))
        assertFalse(loadsGroups(tab = 0, groups = emptyList(), loading = false))
    }

    private var retries = 0

    private fun show(shown: GroupsShown, groups: List<AccessCandidate> = emptyList(), error: MochiError? = null) {
        rule.setContent {
            GroupChoices(
                shown = shown,
                groups = groups,
                error = error,
                noGroups = "No groups",
                selectedSubject = "",
                onSelect = {},
                onRetry = { retries++ },
            )
        }
    }

    private val spinner = hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)

    @Test
    fun `loading shows a spinner, not a claim that there are none`() {
        show(GroupsShown.Loading)
        rule.onNode(spinner).assertExists()
        rule.onNodeWithText("No groups").assertDoesNotExist()
    }

    @Test
    fun `a load that found none says so`() {
        show(GroupsShown.None)
        rule.onNodeWithText("No groups").assertExists()
        rule.onNode(spinner).assertDoesNotExist()
    }

    @Test
    fun `loaded groups are offered`() {
        show(GroupsShown.Offered, groups = friends)
        rule.onNodeWithText("Friends").assertExists()
    }

    @Test
    fun `groups that could not load say why, and the retry asks again`() {
        show(GroupsShown.Failed, error = failure)
        rule.onNodeWithText("Groups are unavailable").assertExists()
        rule.onNodeWithText("No groups").assertDoesNotExist()
        rule.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }
}
