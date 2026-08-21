// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import org.mochios.android.ui.components.AboutDialog
import org.mochios.android.ui.components.DrawerActionRow
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiListDrawer
import org.mochios.market.R
import org.mochios.market.repository.MarketRepository
import org.mochios.android.R as MochiR

/**
 * Common shell for every market screen that the drawer can reach.
 *
 * The drawer lists peers of the browse screen — Saved, Purchases, Bids,
 * Listings, ... — not children of it, so each one carries the drawer rather
 * than a back button: moving between them is one tap, not back-then-forward.
 * Screens further down the hierarchy (listing detail, checkout, create
 * listing) are genuine children and keep
 * [org.mochios.android.ui.components.MochiScaffold] with its back arrow.
 *
 * Mirrors [org.mochios.staff.ui.components.StaffLayout] in intent, with two
 * deliberate differences:
 *
 *  - The content slot takes [PaddingValues] like `MochiScaffold` does, so a
 *    screen keeps control of its own insets (list `contentPadding`, edge-to-
 *    edge bodies) instead of getting a pre-padded Box.
 *  - It never blocks on the account lookup. `isSeller` only decides whether
 *    the drawer's Selling section renders, so a slow or failed call hides
 *    that section rather than holding up the screen — the same "absence is
 *    not seller" reading `HomeViewModel` already applies.
 *
 * @param currentRoute this screen's route, so the drawer highlights it.
 * @param titleRes the top-bar title, as a string resource rather than a
 *   resolved String, so a caller in the nav graph stays a single line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketLayout(
    navController: NavController,
    currentRoute: String,
    @StringRes titleRes: Int,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    layoutViewModel: MarketLayoutViewModel = hiltViewModel(),
    content: @Composable (PaddingValues) -> Unit,
) {
    val isSeller by layoutViewModel.isSeller.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()

    var showAbout by remember { mutableStateOf(false) }
    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }

    MochiListDrawer(
        drawerState = drawerState,
        items = marketDrawerItems(isSeller = isSeller),
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
                                contentDescription = stringResource(R.string.market_open_sidebar),
                            )
                        }
                    },
                    actions = actions,
                )
            },
            floatingActionButton = floatingActionButton,
            snackbarHost = snackbarHost,
            content = content,
        )
    }
}

/**
 * Resolves whether the caller sells, which is all the layout needs from the
 * account: it gates the drawer's Selling section.
 *
 * A failed lookup resolves to `false` rather than surfacing an error — the
 * drawer simply omits Selling, and the screen underneath is unaffected.
 */
@HiltViewModel
class MarketLayoutViewModel @Inject constructor(
    private val repo: MarketRepository,
) : ViewModel() {

    private val _isSeller = MutableStateFlow(false)
    val isSeller: StateFlow<Boolean> = _isSeller.asStateFlow()

    init {
        viewModelScope.launch {
            _isSeller.value = try {
                repo.getAccount().seller == 1
            } catch (_: Exception) {
                false
            }
        }
    }
}
