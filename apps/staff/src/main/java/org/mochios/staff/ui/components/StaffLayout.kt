// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.ui.components.AboutDialog
import org.mochios.android.ui.components.DrawerActionRow
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiListDrawer
import org.mochios.android.ui.components.LoadingState
import org.mochios.staff.R
import org.mochios.staff.model.Me
import org.mochios.staff.repository.StaffRepository
import org.mochios.staff.ws.StaffEventsBus
import org.mochios.staff.ws.rememberStaffEventsSubscription
import org.mochios.android.R as MochiR

/**
 * CompositionLocal exposing the caller's [Me] record to every staff
 * descendant composable. `null` while the layout is still loading or when
 * the caller is not staff (the staff launcher should be hidden in that
 * case, but we still tolerate the null defensively).
 *
 * Screens read role gating off `LocalStaffMe.current?.role` — for example,
 * the configuration screen toggles its "admin required" surface based on
 * this, and the team screen hides admin-only actions for moderators /
 * support.
 */
val LocalStaffMe = compositionLocalOf<Me?> { null }

/**
 * UI state for [StaffLayoutViewModel]. The layout loads `me` once on entry
 * so every descendant screen sees the same role without each VM having to
 * re-fetch it.
 */
sealed class StaffLayoutUiState {
    object Loading : StaffLayoutUiState()
    data class Ready(val me: Me) : StaffLayoutUiState()
    data class Error(val error: MochiError) : StaffLayoutUiState()
}

/**
 * One-shot ViewModel that fetches the caller's staff record via
 * [StaffRepository.getMe]. The Comptroller's `event_staff_me` always
 * succeeds for an authenticated identity (even non-staff get a row with
 * `role=""`) so a successful response with a blank role is legitimate
 * "you're signed in but not staff" — the launcher gate handles that
 * separately; here we just surface whatever the server returned.
 *
 * Holds a reference to the singleton [StaffEventsBus] so the layout can
 * mount the staff-events WebSocket subscription at the shell level (one
 * socket for the whole nav graph rather than per-screen).
 */
@HiltViewModel
class StaffLayoutViewModel @Inject constructor(
    private val repository: StaffRepository,
    val eventsBus: StaffEventsBus,
) : ViewModel() {

    private val _state = MutableStateFlow<StaffLayoutUiState>(StaffLayoutUiState.Loading)
    val state: StateFlow<StaffLayoutUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = StaffLayoutUiState.Loading
            try {
                val me = repository.getMe()
                _state.value = StaffLayoutUiState.Ready(me)
            } catch (e: Exception) {
                _state.value = StaffLayoutUiState.Error(e.toMochiError())
            }
        }
    }
}

/**
 * Common shell for every staff screen.
 *
 * Wires the [MochiListDrawer] carrying [staffDrawerItems] (role-aware),
 * the [TopAppBar] (title + hamburger + caller-supplied [topBarActions]),
 * and the staff-events WebSocket subscription. The current screen's body
 * is the [content] slot.
 *
 * The layout owns the drawer state and the `me` lookup; descendant screens
 * read `LocalStaffMe.current` to drive their own admin-gating UI. Each
 * screen continues to own its own [androidx.compose.material3.SnackbarHost]
 * — screen-local toast events stay close to their producing ViewModel.
 *
 * While `me` is loading the layout renders lib's [LoadingState]; on error
 * it renders [ErrorState] with a retry button. The drawer + topbar are
 * still mounted in those states so the user can navigate away from a
 * stuck screen.
 *
 * @param titleRes the top-bar title, as a string resource rather than a
 *   resolved String: every caller is a route in
 *   [org.mochios.staff.navigation.staffNavGraph] naming a static label, so
 *   resolving it here keeps each of those routes a single line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffLayout(
    navController: NavController,
    currentRoute: String,
    @StringRes titleRes: Int,
    topBarActions: @Composable RowScope.() -> Unit = {},
    layoutViewModel: StaffLayoutViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val state by layoutViewModel.state.collectAsState()

    // Open the staff-events WebSocket once at the layout level so a single
    // connection survives across screen changes. The wrapper closes the
    // socket on dispose, so dropping this composable (e.g. signing out or
    // leaving the staff nav graph) unwinds the connection.
    rememberStaffEventsSubscription(layoutViewModel.eventsBus)

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    val me: Me? = (state as? StaffLayoutUiState.Ready)?.me

    var showAbout by remember { mutableStateOf(false) }
    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }

    CompositionLocalProvider(LocalStaffMe provides me) {
        MochiListDrawer(
            drawerState = drawerState,
            items = staffDrawerItems(userRole = me?.role),
            selectedId = currentRoute,
            onItemClick = { item ->
                drawerScope.launch { drawerState.close() }
                if (item.id != currentRoute) navController.navigate(item.id)
            },
            actions = {
                DrawerActionRow(
                    title = stringResource(MochiR.string.about_label),
                    icon = Icons.Outlined.Info,
                    onClick = {
                        drawerScope.launch { drawerState.close() }
                        showAbout = true
                    },
                )
            },
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(titleRes)) },
                        navigationIcon = {
                            MochiIconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                                Icon(
                                    Icons.Default.Menu,
                                    contentDescription = stringResource(R.string.staff_dashboard_open_sidebar),
                                )
                            }
                        },
                        actions = topBarActions,
                    )
                },
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    when (val s = state) {
                        is StaffLayoutUiState.Loading -> LoadingState()
                        is StaffLayoutUiState.Error -> ErrorState(
                            error = s.error,
                            onRetry = layoutViewModel::load,
                        )
                        is StaffLayoutUiState.Ready -> content()
                    }
                }
            }
        }
    }
}
