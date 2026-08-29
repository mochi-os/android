// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.project

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.HomeMax
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.userMessage
import org.mochios.android.push.SystemNotifications
import org.mochios.android.ui.components.MochiSearchTopBar
import org.mochios.android.ui.components.AboutDialog
import org.mochios.android.ui.components.ColorPicker
import org.mochios.android.ui.components.DrawerActionRow
import org.mochios.android.ui.components.DrawerTitle
import org.mochios.android.ui.components.EntityIconCircle
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.DrawerItem
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiListDrawer
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.ui.components.NotificationBell
import org.mochios.android.ui.components.LastViewedStore
import org.mochios.android.ui.components.NotFoundState
import org.mochios.android.files.MIME_ZIP
import org.mochios.android.files.shareExportFile
import org.mochios.android.files.rememberFileSaveLauncher
import org.mochios.android.ui.components.MochiDropdownMenuDivider
import org.mochios.projects.R
import org.mochios.projects.model.Project
import org.mochios.projects.ui.board.BoardView
import org.mochios.projects.ui.`object`.ObjectDetailSheet
import org.mochios.projects.ui.projectlist.ProjectListViewModel
import org.mochios.projects.ui.router.PROJECTS_FEATURE
import org.mochios.projects.ui.tree.TreeView
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectScreen(
    projectId: String,
    onSelectProject: (String) -> Unit,
    onSelectAll: () -> Unit,
    onFindProjects: () -> Unit,
    onCreateProject: () -> Unit,
    onSettings: (String) -> Unit,
    onDesign: (String) -> Unit,
    onViewDiff: (String, String, String, String) -> Unit,
    onCreateObject: (parent: String?, presetValues: Map<String, String>) -> Unit = { _, _ -> },
    onOpenNotifications: () -> Unit = {},
    onLogout: () -> Unit,
    initialObjectId: String? = null,
    listViewModel: ProjectListViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(
        if (projectId.isEmpty()) DrawerValue.Open else DrawerValue.Closed
    )
    val drawerScope = rememberCoroutineScope()
    val listUiState by listViewModel.uiState.collectAsState()
    var showAbout by remember { mutableStateOf(false) }

    LaunchedEffect(projectId) {
        if (projectId.isNotBlank()) {
            LastViewedStore.set(context, PROJECTS_FEATURE, projectId)
            SystemNotifications.cancelFor(context, "projects", projectId)
        }
    }

    val drawerItems = remember(listUiState.projects) {
        listViewModel.filteredProjects().map { project ->
            DrawerItem(
                id = project.fingerprint.ifEmpty { project.id },
                title = project.name,
                icon = Icons.Outlined.FolderOpen,
            )
        }
    }
    val drawerAll = DrawerItem(
        id = LastViewedStore.ALL,
        title = stringResource(R.string.projects_all_projects),
        icon = Icons.Outlined.FolderOpen,
    )

    MochiListDrawer(
        drawerState = drawerState,
        header = { DrawerTitle(stringResource(R.string.projects_list_title)) },
        items = drawerItems,
        allItem = drawerAll,
        selectedId = projectId,
        onItemClick = { item ->
            drawerScope.launch { drawerState.close() }
            when {
                item.id == LastViewedStore.ALL -> onSelectAll()
                item.id != projectId -> onSelectProject(item.id)
            }
        },
        actions = {
            DrawerActionRow(
                title = stringResource(R.string.projects_list_find),
                icon = Icons.Outlined.Search,
                onClick = {
                    drawerScope.launch { drawerState.close() }
                    onFindProjects()
                },
            )
            DrawerActionRow(
                title = stringResource(R.string.projects_list_create),
                icon = Icons.Outlined.Add,
                onClick = {
                    drawerScope.launch { drawerState.close() }
                    onCreateProject()
                },
            )
            DrawerActionRow(
                title = stringResource(R.string.projects_list_logout),
                icon = Icons.AutoMirrored.Outlined.Logout,
                onClick = {
                    drawerScope.launch { drawerState.close() }
                    onLogout()
                },
            )
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
        when {
            projectId == LastViewedStore.ALL -> {
                AllProjectsContent(
                    onOpenDrawer = { drawerScope.launch { drawerState.open() } },
                    onProjectClick = onSelectProject,
                    onOpenNotifications = onOpenNotifications,
                    viewModel = listViewModel,
                )
            }

            projectId.isEmpty() -> {
                ProjectDrawerPlaceholder(
                    onOpenDrawer = { drawerScope.launch { drawerState.open() } },
                )
            }

            else -> {
                ProjectContent(
                    onOpenDrawer = { drawerScope.launch { drawerState.open() } },
                    onSettings = onSettings,
                    onDesign = onDesign,
                    onViewDiff = onViewDiff,
                    onCreateObject = onCreateObject,
                    onOpenNotifications = onOpenNotifications,
                    initialObjectId = initialObjectId,
                )
            }
        }
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectDrawerPlaceholder(onOpenDrawer: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.projects_list_title)) },
                navigationIcon = {
                    MochiIconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.projects_list_title))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.projects_list_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllProjectsContent(
    onOpenDrawer: () -> Unit,
    onProjectClick: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    viewModel: ProjectListViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            if (uiState.showSearch) {
                MochiSearchTopBar(
                    query = uiState.searchQuery,
                    placeholder = stringResource(R.string.projects_list_search_placeholder),
                    onQueryChange = viewModel::updateSearchQuery,
                    onClose = { viewModel.toggleSearch() }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.projects_all_projects)) },
                    navigationIcon = {
                        MochiIconButton(onClick = onOpenDrawer) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = stringResource(R.string.projects_list_title)
                            )
                        }
                    },
                    actions = {
                        NotificationBell(onClick = onOpenNotifications)
                        MochiIconButton(onClick = { viewModel.toggleSearch() }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.projects_list_search)
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading && uiState.projects.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    uiState.error != null && uiState.projects.isEmpty() -> {
                        ErrorState(
                            error = uiState.error!!,
                            onRetry = { viewModel.loadProjects() }
                        )
                    }

                    else -> {
                        val filteredProjects = viewModel.filteredProjects()
                        if (filteredProjects.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 64.dp),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Text(
                                    text = if (uiState.searchQuery.isNotBlank()) {
                                        stringResource(R.string.projects_list_no_matching)
                                    } else {
                                        stringResource(R.string.projects_list_empty)
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(filteredProjects, key = { it.fingerprint.ifEmpty { it.id } }) { project ->
                                    ProjectRow(
                                        project = project,
                                        onClick = {
                                            val id = project.fingerprint.ifEmpty { project.id }
                                            onProjectClick(id)
                                        },
                                        onUnsubscribe = { viewModel.unsubscribe(project.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Search field that takes over the whole top bar. Shared by the All projects
 * list and the project detail screen — they differ only in [placeholder].
 */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectRow(
    project: Project,
    onClick: () -> Unit,
    onUnsubscribe: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showUnsubscribeConfirm by remember { mutableStateOf(false) }
    val projectId = project.fingerprint.ifEmpty { project.id }
    val canUnsubscribe = project.owner != 1
    val unsubscribeTitle = stringResource(R.string.projects_settings_unsubscribe_title)
    val unsubscribeMessage = stringResource(R.string.projects_settings_unsubscribe_message)
    val unsubscribeLabel = stringResource(R.string.projects_settings_unsubscribe)
    val cancelLabel = stringResource(MochiR.string.common_cancel)
    val description = project.description.takeIf { it.isNotBlank() }

    MochiCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { showMenu = true }),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EntityIconCircle(
                seed = projectId.ifEmpty { project.id },
                icon = Icons.Default.Folder
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box {
                MochiIconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreHoriz,
                        contentDescription = stringResource(MochiR.string.common_more_options)
                    )
                }
                MochiDropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    MochiDropdownMenuItem(
                        text = { Text(stringResource(R.string.projects_list_add_to_home)) },
                        onClick = {
                            showMenu = false
                            // mochi:/<entity> per claude/plans/mochi-uri-scheme.md.
                            // launch-ok: mochi: deep link this app builds from its own id, not an external URL
                            val intent = Intent(Intent.ACTION_VIEW, "mochi:/$projectId".toUri()).apply {
                                setPackage(context.packageName)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                                putExtra("app", "projects")
                            }
                            val shortcut = ShortcutInfoCompat.Builder(context, "project_$projectId")
                                .setShortLabel(project.name)
                                .setLongLabel(project.name)
                                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_projects))
                                .setIntent(intent)
                                .build()
                            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
                        },
                        leadingIcon = { Icon(Icons.Outlined.HomeMax, contentDescription = null) },
                    )
                    if (canUnsubscribe) {
                        MochiDropdownMenuItem(
                            text = { Text(unsubscribeLabel) },
                            onClick = {
                                showMenu = false
                                showUnsubscribeConfirm = true
                            },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        )
                    }
                }
            }
        }
    }

    if (showUnsubscribeConfirm) {
        MochiAlertDialog(
            onDismissRequest = { showUnsubscribeConfirm = false },
            title = unsubscribeTitle,
            text = unsubscribeMessage,
            confirmText = unsubscribeLabel,
            onConfirm = {
                showUnsubscribeConfirm = false
                onUnsubscribe()
            },
            destructive = true,
            dismissText = cancelLabel,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectContent(
    onOpenDrawer: () -> Unit,
    onSettings: (String) -> Unit,
    onDesign: (String) -> Unit,
    onViewDiff: (String, String, String, String) -> Unit,
    onCreateObject: (parent: String?, presetValues: Map<String, String>) -> Unit,
    onOpenNotifications: () -> Unit,
    initialObjectId: String? = null,
    viewModel: ProjectViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showOverflow by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var showAddColumn by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val shareTitle = stringResource(R.string.projects_share_link_title)
    LaunchedEffect(viewModel) {
        viewModel.shareLink.collect { link ->
            shareProjectLink(context, link, shareTitle)
        }
    }

    val exportSaved = stringResource(R.string.projects_export_saved)
    val exportFailed = stringResource(R.string.projects_export_failed)
    // The picker only reports where the file goes; the ViewModel writes it.
    val saveExport = rememberFileSaveLauncher(MIME_ZIP) { uri ->
        if (uri != null) viewModel.writeExportTo(uri) else viewModel.cancelExport()
    }
    LaunchedEffect(uiState.pendingExport) {
        uiState.pendingExport?.let { pending ->
            saveExport.launch(pending.suggestedName)
        }
    }
    // A saved export goes straight to the share sheet. The file is already on
    // disk either way, so backing out of the sheet costs the user nothing.
    LaunchedEffect(uiState.savedExport, uiState.exportFailed) {
        val saved = uiState.savedExport
        if (saved != null) {
            Toast.makeText(context, exportSaved, Toast.LENGTH_SHORT).show()
            shareExportFile(context, saved)
            viewModel.clearExportResult()
        } else if (uiState.exportFailed) {
            Toast.makeText(context, exportFailed, Toast.LENGTH_SHORT).show()
            viewModel.clearExportResult()
        }
    }

    LaunchedEffect(initialObjectId) {
        if (initialObjectId != null) {
            viewModel.selectObject(initialObjectId)
        }
    }

    val details = uiState.projectDetails
    val activeView = viewModel.getActiveView()

    // The overflow icon carries a dot whenever the sheet holds something other
    // than the view's own defaults, so a filtered list still says so from the
    // bar. The search query is deliberately left out — it has its own visible
    // affordance in the top bar.
    val filtersActive = uiState.watchedOnly ||
        uiState.fieldFilters.isNotEmpty() ||
        viewModel.hasSortOverride()

    Scaffold(
        topBar = {
            if (showSearch) {
                MochiSearchTopBar(
                    query = uiState.searchQuery,
                    placeholder = stringResource(R.string.projects_search_objects_placeholder),
                    onQueryChange = viewModel::updateSearchQuery,
                    onClose = {
                        showSearch = false
                        viewModel.updateSearchQuery("")
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            // Only claim to be loading while it is: on a failed
                            // load details stays null, and "Loading…" would sit
                            // there for good beside the error state's retry.
                            text = details?.project?.name ?: stringResource(
                                if (uiState.error != null) R.string.projects_list_title
                                else R.string.projects_loading
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        MochiIconButton(onClick = onOpenDrawer) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = stringResource(R.string.projects_list_title)
                            )
                        }
                    },
                    actions = {
                        NotificationBell(onClick = onOpenNotifications)
                        MochiIconButton(onClick = { showSearch = true }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.projects_search)
                            )
                        }
                        Box {
                            MochiIconButton(onClick = { showOverflow = true }) {
                                BadgedBox(
                                    badge = {
                                        // Says only that something in
                                        // the menu is on; the filter row inside
                                        // carries the same dot to say what.
                                        if (filtersActive) {
                                            Badge(modifier = Modifier.size(6.dp))
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.projects_more)
                                    )
                                }
                            }
                            MochiDropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false }
                            ) {
                                // Filter and sort used to be a fifth control
                                // in the bar. It opens a sheet either way, so it
                                // costs one tap more from in here and gives the
                                // bar back to the actions that act in place.
                                MochiDropdownMenuItem(
                                    text = { Text(stringResource(R.string.projects_filter)) },
                                    onClick = {
                                        showOverflow = false
                                        showFilters = true
                                    },
                                    leadingIcon = {
                                        // The same dot as the bar's, on the row
                                        // it belongs to. A check would read as
                                        // "chosen", which is what the views
                                        // below it mean by one.
                                        BadgedBox(
                                            badge = {
                                                if (filtersActive) {
                                                    Badge(modifier = Modifier.size(6.dp))
                                                }
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.FilterList,
                                                contentDescription = null
                                            )
                                        }
                                    },
                                )
                                MochiDropdownMenuDivider()
                                // Views the project defines, checked one at a
                                // time. Listed even when there is only one, so
                                // the menu always says which view is on screen
                                // and how it is drawn.
                                val views = details?.views.orEmpty()
                                if (views.isNotEmpty()) {
                                    views.forEach { view ->
                                        val isActive = view.id == activeView?.id
                                        MochiDropdownMenuItem(
                                            text = { Text(view.name) },
                                            onClick = {
                                                showOverflow = false
                                                viewModel.setActiveView(view.id)
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    if (view.viewtype == "board") {
                                                        Icons.Outlined.Dashboard
                                                    } else {
                                                        Icons.Outlined.FormatListBulleted
                                                    },
                                                    contentDescription = null,
                                                )
                                            },
                                            selected = isActive,
                                        )
                                    }
                                    MochiDropdownMenuDivider()
                                }

                                // Add column — only meaningful on a board view that
                                // groups by a field (mirrors web's overflow "Add
                                // column"). Creates a new option on the grouping field.
                                if (activeView?.viewtype == "board" && activeView.columns.isNotBlank()) {
                                    MochiDropdownMenuItem(
                                        text = { Text(stringResource(R.string.projects_board_add_column)) },
                                        onClick = {
                                            showOverflow = false
                                            showAddColumn = true
                                        },
                                        leadingIcon = { Icon(Icons.Outlined.ViewColumn, contentDescription = null) },
                                    )
                                }
                                // Sharing a link is only offered on projects the
                                // user owns; it's hidden on subscribed ones.
                                if (details?.project?.owner == 1) {
                                    MochiDropdownMenuItem(
                                        text = { Text(stringResource(R.string.projects_link)) },
                                        onClick = {
                                            showOverflow = false
                                            viewModel.shareProject()
                                        },
                                        leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                                    )
                                }
                                MochiDropdownMenuItem(
                                    text = { Text(stringResource(R.string.projects_settings)) },
                                    onClick = {
                                        showOverflow = false
                                        onSettings(viewModel.projectId)
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                                )
                                // Reshaping the design is the owner's to do, so
                                // it is offered on the same terms as the link
                                // above rather than on every subscribed project.
                                if (details?.project?.owner == 1) {
                                    MochiDropdownMenuItem(
                                        text = { Text(stringResource(R.string.projects_design)) },
                                        onClick = {
                                            showOverflow = false
                                            onDesign(viewModel.projectId)
                                        },
                                        leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                                    )
                                }
                                MochiDropdownMenuItem(
                                    text = { Text(stringResource(R.string.projects_export)) },
                                    onClick = {
                                        showOverflow = false
                                        viewModel.exportProject()
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.FileDownload, contentDescription = null) },
                                )
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            // The board view creates objects from its own per-column plus
            // buttons, so the FAB is only offered on the list views.
            if (details != null && activeView?.viewtype != "board") {
                FloatingActionButton(onClick = { onCreateObject(null, emptyMap()) }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.projects_create_object)
                    )
                }
            }
        },
    ) { padding ->
        // No-op scrollable so pull-to-refresh also fires on the tabs/search
        // header, which is not scrollable on its own.
        val passThroughVerticalScroll = rememberScrollableState { 0f }
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .scrollable(
                        state = passThroughVerticalScroll,
                        orientation = Orientation.Vertical
                    )
            ) {
                // Main content
                when {
                    uiState.isLoading && details == null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    uiState.error is MochiError.NotFoundError && details == null -> {
                        NotFoundState(
                            title = stringResource(R.string.projects_project_not_found),
                            onBack = onOpenDrawer,
                        )
                    }

                    uiState.error != null && details == null -> {
                        ErrorState(
                            error = uiState.error!!,
                            onRetry = { viewModel.loadProject() }
                        )
                    }

                    details != null -> {
                        val filteredObjects = viewModel.getFilteredObjects()
                        val allObjects = uiState.objects
                        when (activeView?.viewtype) {
                            "board" -> {
                                BoardView(
                                    objects = allObjects,
                                    visibleIds = viewModel.getVisibleObjectIds(),
                                    view = activeView,
                                    viewModel = viewModel,
                                    onObjectClick = { viewModel.selectObject(it) },
                                    onCreateObject = { initialValues ->
                                        onCreateObject(null, initialValues)
                                    }
                                )
                            }
                            else -> {
                                TreeView(
                                    objects = filteredObjects,
                                    view = activeView,
                                    viewModel = viewModel,
                                    onObjectClick = { viewModel.selectObject(it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Add-column dialog (board views). Creates a new option on the board's
    // grouping field, mirroring web's overflow "Add column".
    if (showAddColumn && activeView != null && activeView.columns.isNotBlank()) {
        val columnFieldId = activeView.columns
        AddColumnDialog(
            onDismiss = { showAddColumn = false },
            onAdd = { name, colour ->
                viewModel.addColumnOption(columnFieldId, name, colour)
                showAddColumn = false
            }
        )
    }

    // Object detail sheet
    if (uiState.selectedObjectId != null && details != null) {
        ObjectDetailSheet(
            projectId = viewModel.projectId,
            objectId = uiState.selectedObjectId!!,
            projectDetails = details,
            initialObject = uiState.objects.find { it.id == uiState.selectedObjectId },
            viewFieldIds = viewModel.getActiveViewFieldIds(),
            onDismiss = { viewModel.selectObject(null) },
            // deleteObject deletes, clears the selection when it matches, and
            // refreshes — so this must not pre-clear the selection it needs.
            onDeleteObject = { viewModel.deleteObject(uiState.selectedObjectId!!) },
            onViewDiff = onViewDiff,
            onNavigateToObject = { id -> viewModel.selectObject(id) },
            onAddChild = { parentId ->
                // Close the sheet, then open the create form with the parent
                // pre-selected. The form reads project.hierarchy and seeds the
                // class to one that permits this parent.
                viewModel.selectObject(null)
                onCreateObject(parentId, emptyMap())
            },
        )
    }

    if (uiState.isExporting) {
        ExportProgressDialog()
    }

    if (showFilters) {
        SortFilterSheet(
            fieldSortOptions = viewModel.getSortFieldOptions(),
            builtInSortOptions = builtInSortOptions(),
            activeSort = viewModel.getSelectedSortField(),
            activeDirection = viewModel.getActiveSortDirection(),
            filterFields = viewModel.getFilterableFields(),
            activeFieldFilters = uiState.fieldFilters,
            watchedOnly = uiState.watchedOnly,
            onSortChange = { field -> viewModel.setSortField(field) },
            onToggleDirection = { viewModel.toggleSortDirection() },
            onToggleFieldValue = { fieldId, optionId ->
                viewModel.toggleFieldFilter(fieldId, optionId)
            },
            onClearFieldFilter = { fieldId -> viewModel.clearFieldFilter(fieldId) },
            onToggleWatched = { viewModel.toggleWatchedOnly() },
            onClearAll = { viewModel.clearFilters() },
            onDismiss = { showFilters = false },
        )
    }
}

/** Sort keys the server understands regardless of the project's own fields. */
@Composable
private fun builtInSortOptions(): List<Pair<String, String>> = listOf(
    "rank" to stringResource(R.string.projects_sort_rank),
    "number" to stringResource(R.string.projects_sort_number),
    "created" to stringResource(R.string.projects_sort_created),
    "updated" to stringResource(R.string.projects_sort_updated)
)

// Add a new board column (= a new option on the board's grouping field).
// Name + a colour, mirroring web's OptionDialog used for "Add column". Boards
// render the option colour, not its icon, so no icon field here.
@Composable
private fun AddColumnDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, colour: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var colour by remember { mutableStateOf("#3b82f6") }

    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.projects_board_add_column),
        content = {
            // The picker is taller than the dialog on a short screen, so the
            // body scrolls. Its saturation field consumes its own drags, so
            // dragging inside it doesn't scroll the dialog out from under it.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                MochiTextField(
                    value = name,
                    onValueChange = { value -> name = value },
                    label = { Text(stringResource(R.string.projects_field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.projects_option_color),
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                ColorPicker(
                    hex = colour,
                    onHexChange = { hex -> colour = hex },
                )
            }
        },
        confirmText = stringResource(MochiR.string.common_add),
        onConfirm = { onAdd(name.trim(), colour.ifBlank { null }) },
        confirmEnabled = name.isNotBlank(),
        dismissText = stringResource(MochiR.string.common_cancel),
    )
}

/** Opens the system share sheet with the project's [link]. */
private fun shareProjectLink(context: Context, link: String, title: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, link)
        // Names the sheet's content preview. Android 10+ ignores the
        // createChooser title, so without this the sheet reads "Sharing text".
        putExtra(Intent.EXTRA_TITLE, title)
    }
    val chooser = Intent.createChooser(intent, title)
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

@Composable
private fun ExportProgressDialog() {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(stringResource(R.string.projects_exporting))
            }
        }
    }
}

