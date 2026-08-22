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
 * The caller's [Me], provided by [StaffLayout]; null while loading. Screens
 * gate admin-only UI on `current?.role`.
 */
val LocalStaffMe = compositionLocalOf<Me?> { null }

sealed class StaffLayoutUiState {
    object Loading : StaffLayoutUiState()
    data class Ready(val me: Me) : StaffLayoutUiState()
    data class Error(val error: MochiError) : StaffLayoutUiState()
}

/**
 * Loads the caller's [Me] once per layout; a blank role is a legitimate
 * response (signed in, not staff).
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
 * Shell for every staff screen: drawer with [StaffSidebar], top bar, and the
 * staff-events WebSocket. Provides [LocalStaffMe]; the drawer stays mounted in
 * the loading and error states so the user can navigate away.
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
