// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.router

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.mochios.android.ui.components.LastViewedStore

@Composable
fun PeopleRouter(onResolve: (section: String) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        onResolve(peopleSection(LastViewedStore.get(context, PEOPLE_FEATURE).orEmpty()))
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * Records the section the user is looking at, so the next launch lands there.
 * Called by each section screen rather than by the navigation graph, which has
 * no context of its own.
 */
@Composable
fun RememberPeopleSection(section: String) {
    val context = LocalContext.current
    LaunchedEffect(section) {
        LastViewedStore.set(context, PEOPLE_FEATURE, section)
    }
}

/** [LastViewedStore] key for the people module. */
const val PEOPLE_FEATURE = "people"

/** Section tokens written to [LastViewedStore]. */
object PeopleSection {
    const val CONTACTS = "contacts"
    const val INVITATIONS = "invitations"
    const val PROFILE = "profile"

    /** What the module wrote for this section before contacts replaced friends. */
    const val LEGACY_FRIENDS = "friends"

    /** What the groups list wrote, before each group became a row of the drawer. */
    const val LEGACY_GROUPS = "groups"
}

/**
 * The section a stored token names. A token written for a section since
 * retired, friends or the groups list, lands on contacts, so an upgrade opens
 * a section that exists rather than one the drawer no longer has.
 */
fun peopleSection(stored: String): String = when (stored) {
    PeopleSection.LEGACY_FRIENDS, PeopleSection.LEGACY_GROUPS -> PeopleSection.CONTACTS
    else -> stored
}
