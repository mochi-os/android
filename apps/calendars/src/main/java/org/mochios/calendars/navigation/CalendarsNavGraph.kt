// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import org.mochios.calendars.ui.calendar.CalendarScreen
import org.mochios.calendars.ui.devices.ConnectDeviceScreen
import org.mochios.calendars.ui.dialogs.CreateCalendarScreen
import org.mochios.calendars.ui.dialogs.SubscribeCalendarScreen
import org.mochios.calendars.ui.editor.EventEditScreen

object CalendarsApp {
    /** The one screen: the drawer, the toolbar and whichever view was last open. */
    const val HOME = "calendars/views"

    const val CREATE = "calendars/create"
    const val SUBSCRIBE = "calendars/subscribe"
    const val DEVICES = "calendars/devices"
    // The two editor routes sit on separate paths rather than one path with
    // "new" as an id: a single pattern would match both and the nav graph
    // would have to pick between them.
    const val EVENT_NEW = "calendars/events/new?start={start}"
    const val EVENT_EDIT = "calendars/events/edit/{event}?occurrence={occurrence}"

    /** A new event, optionally starting at a moment the user picked out of a grid. */
    fun newEvent(start: Long = 0): String = "calendars/events/new?start=$start"

    /**
     * An event's editor. [occurrence] is the occurrence the user opened, epoch
     * seconds, which "This event" detaches; 0 for one that does not repeat.
     */
    fun event(event: String, occurrence: Long = 0): String =
        "calendars/events/edit/$event?occurrence=$occurrence"
}

fun NavGraphBuilder.calendarsNavGraph(
    navController: NavController,
    onLogout: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
) {
    composable(
        route = CalendarsApp.HOME,
        deepLinks = listOf(navDeepLink { uriPattern = "mochi://calendars" }),
    ) {
        CalendarScreen(
            onCreateCalendar = { navController.navigate(CalendarsApp.CREATE) },
            onSubscribe = { navController.navigate(CalendarsApp.SUBSCRIBE) },
            onConnectDevice = { navController.navigate(CalendarsApp.DEVICES) },
            onNewEvent = { start -> navController.navigate(CalendarsApp.newEvent(start)) },
            onEditEvent = { event, occurrence -> navController.navigate(CalendarsApp.event(event, occurrence)) },
        )
    }

    composable(CalendarsApp.CREATE) {
        CreateCalendarScreen(
            onBack = { navController.popBackStack() },
            onCreated = { navController.popBackStack() },
        )
    }

    composable(CalendarsApp.SUBSCRIBE) {
        SubscribeCalendarScreen(
            onBack = { navController.popBackStack() },
            onSubscribed = { navController.popBackStack() },
        )
    }

    composable(CalendarsApp.DEVICES) {
        ConnectDeviceScreen(onBack = { navController.popBackStack() })
    }

    composable(
        route = CalendarsApp.EVENT_NEW,
        arguments = listOf(
            navArgument("start") {
                type = NavType.StringType
                defaultValue = "0"
            },
        ),
    ) {
        EventEditScreen(
            onBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
            onDeleted = { navController.popBackStack() },
        )
    }

    composable(
        route = CalendarsApp.EVENT_EDIT,
        arguments = listOf(
            navArgument("event") { type = NavType.StringType },
            navArgument("occurrence") {
                type = NavType.StringType
                defaultValue = "0"
            },
        ),
    ) {
        EventEditScreen(
            onBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
            onDeleted = { navController.popBackStack() },
        )
    }
}
