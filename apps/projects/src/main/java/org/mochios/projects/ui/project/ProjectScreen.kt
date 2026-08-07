// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.project

import android.content.ClipData
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HomeMax
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.userMessage
import org.mochios.android.push.SystemNotifications
import org.mochios.android.ui.components.AboutDialog
import org.mochios.android.ui.components.ColorPicker
import org.mochios.android.ui.components.ConfirmDialog
import org.mochios.android.ui.components.DrawerActionRow
import org.mochios.android.ui.components.EntityIconCircle
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.FeatureDrawerItem
import org.mochios.android.ui.components.FeatureListDrawer
import org.mochios.android.ui.components.NotificationBell
import org.mochios.android.ui.components.LastViewedStore
import org.mochios.android.ui.components.NotFoundState
import org.mochios.android.files.MIME_ZIP
import org.mochios.android.files.SavedExport
import org.mochios.android.files.rememberFileSaveLauncher
import org.mochios.projects.R
import org.mochios.projects.model.Project
import org.mochios.projects.ui.board.BoardView
import org.mochios.projects.ui.`object`.ObjectDetailSheet
import org.mochios.projects.ui.projectlist.CreateProjectDialog
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
    onSettings: (String) -> Unit,
    onDesign: (String) -> Unit,
    onViewDiff: (String, String, String, String) -> Unit,
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

    // Open the project detail right after a create, then clear the signal so
    // it doesn't re-fire on recomposition.
    LaunchedEffect(listUiState.createdProjectId) {
        listUiState.createdProjectId?.let { newId ->
            onSelectProject(newId)
            listViewModel.consumeCreatedProject()
        }
    }

    val drawerItems = remember(listUiState.projects) {
        listViewModel.filteredProjects().map { project ->
            FeatureDrawerItem(
                id = project.fingerprint.ifEmpty { project.id },
                title = project.name,
                icon = Icons.Default.FolderOpen,
            )
        }
    }
    val drawerAll = FeatureDrawerItem(
        id = LastViewedStore.ALL,
        title = stringResource(R.string.projects_all_projects),
        icon = Icons.Default.FolderOpen,
    )

    FeatureListDrawer(
        drawerState = drawerState,
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
                icon = Icons.Default.Search,
                onClick = {
                    drawerScope.launch { drawerState.close() }
                    onFindProjects()
                },
            )
            DrawerActionRow(
                title = stringResource(R.string.projects_list_create),
                icon = Icons.Default.Add,
                onClick = {
                    drawerScope.launch { drawerState.close() }
                    listViewModel.showCreateDialog()
                },
            )
            DrawerActionRow(
                title = stringResource(R.string.projects_list_logout),
                icon = Icons.AutoMirrored.Filled.Logout,
                onClick = {
                    drawerScope.launch { drawerState.close() }
                    onLogout()
                },
            )
            DrawerActionRow(
                title = stringResource(MochiR.string.about_label),
                icon = Icons.Default.Info,
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
                    onOpenNotifications = onOpenNotifications,
                    initialObjectId = initialObjectId,
                )
            }
        }
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }

    if (listUiState.showCreateDialog) {
        CreateProjectDialog(
            templates = listUiState.templates,
            isCreating = listUiState.isCreating,
            backupPrefill = listUiState.backupPrefill,
            onPickBackup = { uri -> listViewModel.readBackup(uri) },
            onDismiss = { listViewModel.hideCreateDialog() },
            onCreate = { name, prefix, privacy, template, backupJson ->
                listViewModel.createProject(name, prefix, privacy, template, backupJson)
            }
        )
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
                    IconButton(onClick = onOpenDrawer) {
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
                ProjectSearchBar(
                    query = uiState.searchQuery,
                    placeholder = stringResource(R.string.projects_list_search_placeholder),
                    onQueryChange = viewModel::updateSearchQuery,
                    onClose = { viewModel.toggleSearch() }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.projects_all_projects)) },
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = stringResource(R.string.projects_list_title)
                            )
                        }
                    },
                    actions = {
                        NotificationBell(onClick = onOpenNotifications)
                        IconButton(onClick = { viewModel.toggleSearch() }) {
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectSearchBar(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(MochiR.string.common_back)
                )
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(placeholder) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(MochiR.string.common_close)
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }
    )
}

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

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
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
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreHoriz,
                        contentDescription = stringResource(MochiR.string.common_more_options)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.projects_list_add_to_home)) },
                        leadingIcon = { Icon(Icons.Default.HomeMax, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            // mochi:/<entity> per claude/plans/mochi-uri-scheme.md.
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
                        }
                    )
                    if (canUnsubscribe) {
                        DropdownMenuItem(
                            text = { Text(unsubscribeLabel) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showUnsubscribeConfirm = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showUnsubscribeConfirm) {
        ConfirmDialog(
            title = unsubscribeTitle,
            message = unsubscribeMessage,
            confirmLabel = unsubscribeLabel,
            dismissLabel = cancelLabel,
            isDestructive = true,
            onConfirm = {
                showUnsubscribeConfirm = false
                onUnsubscribe()
            },
            onDismiss = { showUnsubscribeConfirm = false }
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

    // The top-bar icon carries a dot whenever the sheet holds something other
    // than the view's own defaults. The search query is deliberately left out —
    // it has its own visible affordance in the top bar.
    val filtersActive = uiState.watchedOnly ||
        uiState.fieldFilters.isNotEmpty() ||
        viewModel.hasSortOverride()

    Scaffold(
        topBar = {
            if (showSearch) {
                ProjectSearchBar(
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
                        IconButton(onClick = onOpenDrawer) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = stringResource(R.string.projects_list_title)
                            )
                        }
                    },
                    actions = {
                        NotificationBell(onClick = onOpenNotifications)
                        IconButton(onClick = { showSearch = true }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.projects_search)
                            )
                        }
                        IconButton(onClick = { showFilters = !showFilters }) {
                            BadgedBox(
                                badge = {
                                    if (filtersActive) {
                                        Badge(modifier = Modifier.size(6.dp))
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.FilterList,
                                    contentDescription = stringResource(R.string.projects_filter)
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { showOverflow = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.projects_more)
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false }
                            ) {
                                // Views the project defines, checked one at a
                                // time. Listed even when there is only one, so
                                // the menu always says which view is on screen
                                // and how it is drawn.
                                val views = details?.views.orEmpty()
                                if (views.isNotEmpty()) {
                                    views.forEach { view ->
                                        val isActive = view.id == activeView?.id
                                        DropdownMenuItem(
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
                                                    tint = if (isActive) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        LocalContentColor.current
                                                    },
                                                )
                                            },
                                            trailingIcon = {
                                                if (isActive) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            },
                                        )
                                    }
                                    HorizontalDivider()
                                }

                                // Add column — only meaningful on a board view that
                                // groups by a field (mirrors web's overflow "Add
                                // column"). Creates a new option on the grouping field.
                                if (activeView?.viewtype == "board" && activeView.columns.isNotBlank()) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.projects_board_add_column)) },
                                        onClick = {
                                            showOverflow = false
                                            showAddColumn = true
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.ViewColumn, contentDescription = null)
                                        }
                                    )
                                }
                                // Sharing a link is only offered on projects the
                                // user owns; it's hidden on subscribed ones.
                                if (details?.project?.owner == 1) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.projects_link)) },
                                        onClick = {
                                            showOverflow = false
                                            viewModel.shareProject()
                                        },
                                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.projects_settings)) },
                                    onClick = {
                                        showOverflow = false
                                        onSettings(viewModel.projectId)
                                    },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                                )
                                // Reshaping the design is the owner's to do, so
                                // it is offered on the same terms as the link
                                // above rather than on every subscribed project.
                                if (details?.project?.owner == 1) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.projects_design)) },
                                        onClick = {
                                            showOverflow = false
                                            onDesign(viewModel.projectId)
                                        },
                                        leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.projects_export)) },
                                    onClick = {
                                        showOverflow = false
                                        viewModel.exportProject()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.FileDownload, contentDescription = null)
                                    }
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
                FloatingActionButton(onClick = { viewModel.showCreateObjectDialog() }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.projects_create_object)
                    )
                }
            }
        },
    ) { padding ->
        // No-op vertical scrollable so the tabs/search header above the
        // columns also dispatches pull-down gestures up to PullToRefreshBox.
        // (Tabs and the search bar aren't scrollable on their own, so without
        // this modifier pull-to-refresh wouldn't fire when the user pulls on
        // the top section.)
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
                                        viewModel.showCreateObjectDialog(values = initialValues)
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

    // Create object dialog
    if (uiState.showCreateObjectDialog && details != null) {
        CreateObjectDialog(
            classes = details.classes,
            hierarchy = details.hierarchy,
            objects = uiState.objects,
            presetParent = uiState.createObjectParent,
            presetValues = uiState.createObjectValues,
            isCreating = uiState.isCreatingObject,
            activeView = activeView,
            viewModel = viewModel,
            onDismiss = { viewModel.hideCreateObjectDialog() },
            onCreate = { classId, title, parent, initialValues ->
                viewModel.createObject(classId, title, parent, initialValues)
            }
        )
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
            onDismiss = { viewModel.selectObject(null) },
            // deleteObject deletes, clears the selection when it matches, and
            // refreshes — so this must not pre-clear the selection it needs.
            onDeleteObject = { viewModel.deleteObject(uiState.selectedObjectId!!) },
            onViewDiff = onViewDiff,
            onNavigateToObject = { id -> viewModel.selectObject(id) },
            onAddChild = { parentId ->
                // Close the sheet, then open the create dialog with the
                // parent pre-selected. The dialog reads project.hierarchy
                // and seeds the class to one that permits this parent.
                viewModel.selectObject(null)
                viewModel.showCreateObjectDialog(parent = parentId)
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.projects_board_add_column)) },
        text = {
            // The picker is taller than the dialog on a short screen, so the
            // body scrolls. Its saturation field consumes its own drags, so
            // dragging inside it doesn't scroll the dialog out from under it.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
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
        confirmButton = {
            TextButton(
                onClick = { onAdd(name.trim(), colour.ifBlank { null }) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(MochiR.string.common_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MochiR.string.common_cancel))
            }
        }
    )
}

/** Opens the system share sheet with the project's [link]. */
/**
 * Opens the system share sheet with a finished export.
 *
 * The uri is the document the picker handed back, so the grant has to travel
 * with the intent for the receiving app to read it — as a flag, and as
 * [ClipData], which is what makes the grant stick on the targets that ignore
 * `EXTRA_STREAM` alone.
 */
private fun shareExportFile(context: Context, export: SavedExport) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = export.mimeType
        putExtra(Intent.EXTRA_STREAM, export.uri)
        // Names the sheet's content preview — see shareProjectLink.
        putExtra(Intent.EXTRA_TITLE, export.name)
        clipData = ClipData.newRawUri(export.name, export.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, export.name)
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(chooser)
}

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

