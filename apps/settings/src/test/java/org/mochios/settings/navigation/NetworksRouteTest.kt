// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.util.isAt
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Networks opens plain from the settings home and People, and at a network
 * from an app's Set up; the shell asks isAt before opening either, so as not
 * to open the screen it is on again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NetworksRouteTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var navigation: NavHostController

    private fun start() {
        rule.setContent {
            val controller = rememberNavController()
            navigation = controller
            NavHost(navController = controller, startDestination = SettingsApp.HOME) {
                composable(SettingsApp.HOME) { Text("home") }
                // Declared as the settings graph declares it.
                composable(
                    SettingsApp.NETWORKS,
                    arguments = listOf(
                        navArgument("network") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) { Text("networks") }
            }
        }
        rule.waitForIdle()
    }

    private fun go(route: String) {
        rule.runOnIdle { navigation.navigate(route) { launchSingleTop = true } }
        rule.waitForIdle()
    }

    private fun network(): String? = navigation.currentBackStackEntry?.arguments?.getString("network")

    @Test
    fun `plain Networks opens with no network, and is the screen it is on`() {
        start()
        go(SettingsApp.networks())
        assertEquals(SettingsApp.NETWORKS, navigation.currentBackStackEntry?.destination?.route)
        assertNull(network())
        assertTrue(rule.runOnIdle { navigation.isAt(SettingsApp.networks()) })
        assertFalse(rule.runOnIdle { navigation.isAt(SettingsApp.networks("fediverse")) })
    }

    @Test
    fun `an app's Set up opens Networks at its network`() {
        start()
        go(SettingsApp.networks("fediverse"))
        assertEquals("fediverse", network())
        assertTrue(rule.runOnIdle { navigation.isAt(SettingsApp.networks("fediverse")) })
        assertFalse(rule.runOnIdle { navigation.isAt(SettingsApp.networks("bluesky")) })
    }

    @Test
    fun `the route carries the network as one query value`() {
        assertEquals("settings/networks", SettingsApp.networks())
        assertEquals("settings/networks", SettingsApp.networks(""))
        assertEquals("settings/networks?network=fediverse", SettingsApp.networks("fediverse"))
    }
}
