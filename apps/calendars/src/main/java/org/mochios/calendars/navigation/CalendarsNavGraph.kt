// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.navigation

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import org.mochios.calendars.model.Instance
import org.mochios.calendars.ui.calendar.CalendarScreen
import org.mochios.calendars.ui.devices.ConnectDeviceScreen
import org.mochios.calendars.ui.dialogs.CreateCalendarScreen
import org.mochios.calendars.ui.dialogs.SubscribeCalendarScreen
import org.mochios.calendars.ui.editor.EventEditScreen
import org.mochios.calendars.ui.editor.Scope
import java.net.URLEncoder

object CalendarsApp {
    /** The one screen: the drawer, the toolbar and whichever view was last open. */
    const val HOME = "calendars/views"

    const val CREATE = "calendars/create"
    const val SUBSCRIBE = "calendars/subscribe"
    const val DEVICES = "calendars/devices"
    // The two editor routes sit on separate paths rather than one path with
    // "new" as an id: a single pattern would match both and the nav graph
    // would have to pick between them.
    // The new-event route also opens a copy: `source` says what the form is
    // filled from - `event` for a stored event the editor loads, named by
    // `copy`, or `occurrence` for one it cannot load, whose fields the route
    // carries itself - and is blank for a blank form.
    const val EVENT_NEW = "calendars/events/new?start={start}&source={source}&copy={copy}" +
        "&occurrence={occurrence}&scope={scope}&finish={finish}&allday={allday}&date={date}" +
        "&summary={summary}&location={location}&description={description}&zones={zones}"
    const val EVENT_EDIT = "calendars/events/edit/{event}?occurrence={occurrence}"

    /** The flag the calendar screen's back-stack entry carries once a copy is saved. */
    const val COPIED = "copied"

    /** A new event, optionally starting at a moment the user picked out of a grid. */
    fun newEvent(start: Long = 0): String = "calendars/events/new?start=$start"

    /**
     * An event's editor. [occurrence] is the occurrence the user opened, epoch
     * seconds, which "This event" detaches; 0 for one that does not repeat.
     */
    fun event(event: String, occurrence: Long = 0): String =
        "calendars/events/edit/$event?occurrence=$occurrence"

    /**
     * The editor on a copy of a stored event, as a new event. [occurrence] is
     * the occurrence copied, as the event's tree names it, and [scope] says
     * whether the copy is of that occurrence alone or of the whole series;
     * both are moot for an event that does not repeat.
     */
    fun copyEvent(event: String, occurrence: Long, scope: Scope): String =
        "calendars/events/new?source=event&copy=${encoded(event)}&occurrence=$occurrence" +
            "&scope=${scope.name.lowercase()}"

    /**
     * The editor on a copy of an occurrence the editor cannot load - a
     * subscription's or a birthday - as a new event. The route carries the
     * occurrence itself: its ends, its date when all day, its title,
     * location and description, and the zones its ends were written in,
     * start then finish.
     */
    fun copyOccurrence(instance: Instance): String =
        "calendars/events/new?source=occurrence&start=${instance.start}&finish=${instance.finish}" +
            "&allday=${if (instance.allday) 1 else 0}&date=${encoded(instance.date.orEmpty())}" +
            "&summary=${encoded(instance.summary)}&location=${encoded(instance.location)}" +
            "&description=${encoded(instance.description)}" +
            "&zones=${encoded(instance.zone?.start.orEmpty() + "," + instance.zone?.finish.orEmpty())}"

    /** A value as a query parameter, with a space as `%20`, which the route reads back as one. */
    private fun encoded(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

fun NavGraphBuilder.calendarsNavGraph(
    navController: NavController,
    onLogout: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
) {
    composable(
        route = CalendarsApp.HOME,
        deepLinks = listOf(navDeepLink { uriPattern = "mochi://calendars" }),
    ) { entry ->
        // A saved copy sets the flag on this entry on its way back, and the
        // screen says so once.
        val copied by entry.savedStateHandle.getStateFlow(CalendarsApp.COPIED, false).collectAsState()
        CalendarScreen(
            onCreateCalendar = { navController.navigate(CalendarsApp.CREATE) },
            onSubscribe = { navController.navigate(CalendarsApp.SUBSCRIBE) },
            onConnectDevice = { navController.navigate(CalendarsApp.DEVICES) },
            onNewEvent = { start -> navController.navigate(CalendarsApp.newEvent(start)) },
            onEditEvent = { event, occurrence -> navController.navigate(CalendarsApp.event(event, occurrence)) },
            onCopyEvent = { event, occurrence, scope ->
                navController.navigate(CalendarsApp.copyEvent(event, occurrence, scope))
            },
            onCopyOccurrence = { instance -> navController.navigate(CalendarsApp.copyOccurrence(instance)) },
            copied = copied,
            onCopiedShown = { entry.savedStateHandle[CalendarsApp.COPIED] = false },
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
        ) + listOf(
            "source", "copy", "occurrence", "scope", "finish", "allday", "date",
            "summary", "location", "description", "zones",
        ).map { name ->
            navArgument(name) {
                type = NavType.StringType
                defaultValue = ""
            }
        },
    ) {
        EventEditScreen(
            onBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
            onCopied = {
                // A copy may have been opened from the original's editor;
                // the calendar is where a saved copy shows.
                navController.getBackStackEntry(CalendarsApp.HOME).savedStateHandle[CalendarsApp.COPIED] = true
                navController.popBackStack(CalendarsApp.HOME, inclusive = false)
            },
            onDeleted = { navController.popBackStack() },
            onCopy = { event, occurrence, scope ->
                navController.navigate(CalendarsApp.copyEvent(event, occurrence, scope))
            },
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
            onCopied = { navController.popBackStack() },
            onDeleted = { navController.popBackStack() },
            onCopy = { event, occurrence, scope ->
                navController.navigate(CalendarsApp.copyEvent(event, occurrence, scope))
            },
        )
    }
}
