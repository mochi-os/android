// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DrawerState
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mochios.android.ui.components.AboutDialog
import org.mochios.android.ui.components.DrawerActionRow
import org.mochios.android.ui.components.DrawerItem
import org.mochios.android.ui.components.DrawerTitle
import org.mochios.android.ui.components.MochiListDrawer
import org.mochios.android.util.NaturalCompare
import org.mochios.wikis.R
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.navigation.WikisApp
import org.mochios.wikis.repository.WikisRepository
import javax.inject.Inject
import org.mochios.android.R as MochiR

/**
 * Feeds [WikiDrawer] on screens that hold no wiki list of their own (the page
 * view). Reads the repository's cached list so hopping between pages inside a
 * wiki doesn't re-fetch it, and refreshes only when that cache is cold. The
 * wiki list screen has its own list and passes it in directly.
 */
@HiltViewModel
class WikiDrawerViewModel @Inject constructor(
    private val repo: WikisRepository,
) : ViewModel() {

    val wikis: StateFlow<List<WikiInfo>> = repo.wikiList
        .map { list -> list.sortedWith(compareBy(NaturalCompare) { it.name }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        if (repo.wikiList.value.isEmpty()) load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                repo.getClassInfo()
            } catch (_: Exception) {
                // The sidebar is a way to get somewhere else, not the screen's
                // content: a failed list leaves it empty rather than raising an
                // error over a page that loaded perfectly well.
            }
        }
    }
}

/**
 * The wikis app's sidebar: the pinned "All wikis" row above [wikis], with
 * find, create and about in the bottom action slot. Shared by the wiki list and
 * the page view so the same list is one gesture away from either, and
 * presentation only — the caller owns [drawerState] so its top bar can open the
 * drawer, and supplies [wikis] from whichever ViewModel it already has.
 *
 * About is the one action the drawer owns outright, dialog and all: it says the
 * same thing wherever it is opened from, so every host wiring up identical
 * state would only be a way for them to drift apart.
 *
 * [selectedId] is the wiki being shown, or [WikisApp.HOME] on the list screen.
 */
@Composable
fun WikiDrawer(
    drawerState: DrawerState,
    wikis: List<WikiInfo>,
    selectedId: String,
    onSelectWiki: (String) -> Unit,
    onSelectAll: () -> Unit,
    onFind: () -> Unit,
    onCreate: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showAbout by remember { mutableStateOf(false) }
    val items = remember(wikis) {
        wikis.map { wiki ->
            DrawerItem(
                id = wiki.fingerprint ?: wiki.id,
                title = wiki.name,
                icon = Icons.Default.MenuBook,
            )
        }
    }

    MochiListDrawer(
        drawerState = drawerState,
        header = { DrawerTitle(stringResource(R.string.wikis_sidebar_header)) },
        items = items,
        allItem = DrawerItem(
            id = WikisApp.HOME,
            title = stringResource(R.string.wikis_sidebar_all),
            icon = Icons.Default.MenuBook,
        ),
        selectedId = selectedId,
        onItemClick = { item ->
            scope.launch { drawerState.close() }
            when {
                item.id == WikisApp.HOME -> onSelectAll()
                item.id != selectedId -> onSelectWiki(item.id)
            }
        },
        actions = {
            DrawerActionRow(
                title = stringResource(R.string.wikis_sidebar_find),
                icon = Icons.Default.Search,
                onClick = {
                    scope.launch { drawerState.close() }
                    onFind()
                },
            )
            DrawerActionRow(
                title = stringResource(R.string.wikis_sidebar_create),
                icon = Icons.Default.Add,
                onClick = {
                    scope.launch { drawerState.close() }
                    onCreate()
                },
            )
            DrawerActionRow(
                title = stringResource(MochiR.string.about_label),
                icon = Icons.Outlined.Info,
                onClick = {
                    scope.launch { drawerState.close() }
                    showAbout = true
                },
            )
        },
        content = content,
    )

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

/**
 * [WikiDrawer] wired to the nav graph, keeping the wiki-switching policy in
 * one place rather than inline in the page view. The wiki list uses
 * [WikiDrawer] directly instead: it already holds the list, and stays put
 * when "All wikis" is tapped.
 *
 * The drawer lives on the wiki list and the page view only, mirroring the
 * projects and crm apps, where it belongs to the screen that hosts both the
 * "All" list and the entity itself. Everything below keeps a plain back arrow.
 */
@Composable
fun WikiNavDrawer(
    navController: NavController,
    wikiId: String,
    drawerState: DrawerState,
    viewModel: WikiDrawerViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val wikis by viewModel.wikis.collectAsState()

    WikiDrawer(
        drawerState = drawerState,
        wikis = wikis,
        selectedId = wikiId,
        onSelectWiki = { id ->
            // Swapping wikis from inside one replaces it rather than stacking
            // on it, so Back still lands on the wiki list rather than walking
            // back through every wiki the user browsed through.
            navController.navigate(WikisApp.wikiHome(id)) {
                popUpTo(WikisApp.HOME)
                launchSingleTop = true
            }
        },
        onSelectAll = {
            if (!navController.popBackStack(WikisApp.HOME, false)) {
                navController.navigate(WikisApp.HOME)
            }
        },
        onFind = { navController.navigate(WikisApp.FIND) },
        onCreate = { navController.navigate(WikisApp.CREATE) },
        content = content,
    )
}
