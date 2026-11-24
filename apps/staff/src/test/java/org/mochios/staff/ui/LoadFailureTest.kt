// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.compose.rememberNavController
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.R as MochiR
import org.mochios.staff.R
import org.mochios.staff.StaffServer
import org.mochios.staff.StaffServer.Companion.ok
import org.mochios.staff.StaffServer.Companion.refused
import org.mochios.staff.ui.categories.CategoriesScreen
import org.mochios.staff.ui.categories.CategoriesViewModel
import org.mochios.staff.ui.config.ConfigScreen
import org.mochios.staff.ui.config.ConfigViewModel
import org.mochios.staff.ui.reviews.ReviewsScreen
import org.mochios.staff.ui.reviews.ReviewsViewModel
import org.mochios.staff.ui.team.TeamScreen
import org.mochios.staff.ui.team.TeamViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A screen whose list could not be read says why, with a retry, rather than
 * showing an empty list; the configuration shows no fields it never read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LoadFailureTest {

    @get:Rule
    val rule = createComposeRule()

    @Volatile private var refuse = true

    private val server = StaffServer { request ->
        val path = request.requestUrl!!.encodedPath
        when {
            refuse -> refused("Not reachable now")
            path.endsWith("-/reviews/list") -> ok("""{"reviews": [], "total": 0, "next": ""}""")
            path.endsWith("-/categories/list") -> ok("[]")
            path.endsWith("-/config/get") -> ok("[]")
            path.endsWith("-/moderation/thresholds") -> ok("""{"low": 30, "high": 70}""")
            path.endsWith("-/team/list") -> ok("[]")
            else -> null
        }
    }

    @Before
    fun begin() {
        server.start()
    }

    @After
    fun end() = server.stop()

    private fun retry() {
        refuse = false
        rule.onNode(hasText(server.context.getString(MochiR.string.common_retry)) and hasClickAction()).performClick()
    }

    @Test
    fun `reviews that could not be read say why`() {
        val model = ReviewsViewModel(server.repository, SavedStateHandle())
        rule.setContent { ReviewsScreen(viewModel = model) }
        server.until { !model.state.value.isLoading }
        rule.onNodeWithText("Not reachable now").assertExists()
        rule.onAllNodesWithText(server.context.getString(R.string.staff_reviews_empty)).assertCountEquals(0)
        retry()
        server.until { server.made("-/reviews/list").size == 2 && !model.state.value.isLoading }
        rule.onNodeWithText(server.context.getString(R.string.staff_reviews_empty)).assertExists()
    }

    @Test
    fun `categories that could not be read say why`() {
        val model = CategoriesViewModel(server.repository)
        rule.setContent {
            CategoriesScreen(navController = rememberNavController(), snackbarHostState = SnackbarHostState(), viewModel = model)
        }
        server.until { !model.state.value.isLoading }
        rule.onNodeWithText("Not reachable now").assertExists()
        rule.onAllNodesWithText(server.context.getString(R.string.staff_categories_empty)).assertCountEquals(0)
        retry()
        server.until { server.made("-/categories/list").size == 2 }
    }

    @Test
    fun `a team that could not be read says why`() {
        val model = TeamViewModel(server.repository)
        rule.setContent { TeamScreen(snackbarHostState = SnackbarHostState(), viewModel = model) }
        server.until { !model.state.value.isLoading }
        rule.onNodeWithText("Not reachable now").assertExists()
        rule.onAllNodesWithText(server.context.getString(R.string.staff_team_empty)).assertCountEquals(0)
        retry()
        server.until { server.made("-/team/list").size == 2 }
    }

    @Test
    fun `a configuration that could not be read shows no fields`() {
        val model = ConfigViewModel(server.repository)
        rule.setContent { ConfigScreen(viewModel = model) }
        server.until { !model.state.value.isLoading }
        rule.onNodeWithText("Not reachable now").assertExists()
        rule.onAllNodesWithText(server.context.getString(R.string.staff_config_field_platform_fee)).assertCountEquals(0)
        rule.onAllNodesWithText(server.context.getString(R.string.staff_config_save)).assertCountEquals(0)
        retry()
        server.until { model.state.value.loaded }
        rule.waitForIdle()
        rule.onNodeWithText(server.context.getString(R.string.staff_config_field_platform_fee)).assertExists()
    }
}
