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

/**
 * Start destination for the people nav graph. Resolves the section the user
 * was last viewing (friends / invitations / groups / profile) from
 * [LastViewedStore] and emits a route token via [onResolve]; the nav graph
 * maps that token onto the matching screen. Empty / unrecognised values
 * fall through to the friends section.
 */
@Composable
fun PeopleRouter(onResolve: (section: String) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        onResolve(LastViewedStore.get(context, PEOPLE_FEATURE).orEmpty())
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** [LastViewedStore] key for the people module. */
const val PEOPLE_FEATURE = "people"

/** Section tokens written to [LastViewedStore]. */
object PeopleSection {
    const val FRIENDS = "friends"
    const val INVITATIONS = "invitations"
    const val GROUPS = "groups"
    const val PROFILE = "profile"
}
