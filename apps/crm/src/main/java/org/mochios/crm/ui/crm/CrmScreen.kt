// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.crm

import android.content.Intent
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HomeMax
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import org.mochios.android.ui.components.ConfirmDialog
import org.mochios.android.ui.components.DrawerActionRow
import org.mochios.android.ui.components.EntityIconCircle
import org.mochios.android.ui.components.FeatureDrawerItem
import org.mochios.android.ui.components.FeatureListDrawer
import org.mochios.android.ui.components.NotificationBell
import org.mochios.android.ui.components.LastViewedStore
import org.mochios.android.ui.components.NotFoundState
import org.mochios.crm.R
import org.mochios.crm.model.Crm
import org.mochios.crm.ui.board.BoardView
import org.mochios.crm.ui.board.parseColor
import org.mochios.crm.ui.`object`.ObjectDetailSheet
import org.mochios.crm.ui.crmlist.CreateCrmDialog
import org.mochios.crm.ui.crmlist.CrmListViewModel
import org.mochios.crm.ui.router.PROJECTS_FEATURE
import org.mochios.crm.ui.tree.TreeView
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrmScreen(
    crmId: String,
    onSelectCrm: (String) -> Unit,
    onSelectAll: () -> Unit,
    onFindCrms: () -> Unit,
    onSettings: (String) -> Unit,
    onDesign: (String) -> Unit,
    onOpenNotifications: () -> Unit = {},
    onLogout: () -> Unit,
    initialObjectId: String? = null,
    listViewModel: CrmListViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(
        if (crmId.isEmpty()) DrawerValue.Open else DrawerValue.Closed
    )
    val drawerScope = rememberCoroutineScope()
    val listUiState by listViewModel.uiState.collectAsState()
    var showAbout by remember { mutableStateOf(false) }

    LaunchedEffect(crmId) {
        if (crmId.isNotBlank()) {
            LastViewedStore.set(context, PROJECTS_FEATURE, crmId)
            SystemNotifications.cancelFor(context, "crm", crmId)
        }
    }

    // Open the CRM detail right after a create, then clear the signal so
    // it doesn't re-fire on recomposition.
    LaunchedEffect(listUiState.createdCrmId) {
        listUiState.createdCrmId?.let { newId ->
            onSelectCrm(newId)
            listViewModel.consumeCreatedCrm()
        }
    }

    val drawerItems = remember(listUiState.crm) {
        listViewModel.filteredCrm().map { crm ->
            FeatureDrawerItem(
                id = crm.fingerprint.ifEmpty { crm.id },
                title = crm.name,
                icon = Icons.Default.FolderOpen,
            )
        }
    }
    val drawerAll = FeatureDrawerItem(
        id = LastViewedStore.ALL,
        title = stringResource(R.string.crm_all_crms),
        icon = Icons.Default.FolderOpen,
    )

    FeatureListDrawer(
        drawerState = drawerState,
        items = drawerItems,
        allItem = drawerAll,
        selectedId = crmId,
        onItemClick = { item ->
            drawerScope.launch { drawerState.close() }
            when {
                item.id == LastViewedStore.ALL -> onSelectAll()
                item.id != crmId -> onSelectCrm(item.id)
            }
        },
        actions = {
            DrawerActionRow(
                title = stringResource(R.string.crm_list_find),
                icon = Icons.Default.Search,
                onClick = {
                    drawerScope.launch { drawerState.close() }
                    onFindCrms()
                },
            )
            DrawerActionRow(
                title = stringResource(R.string.crm_list_create),
                icon = Icons.Default.Add,
                onClick = {
                    drawerScope.launch { drawerState.close() }
                    listViewModel.showCreateDialog()
                },
            )
            DrawerActionRow(
                title = stringResource(R.string.crm_list_logout),
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
            crmId == LastViewedStore.ALL -> {
                AllCrmsContent(
                    onOpenDrawer = { drawerScope.launch { drawerState.open() } },
                    onCrmClick = onSelectCrm,
                    onOpenNotifications = onOpenNotifications,
                    viewModel = listViewModel,
                )
            }

            crmId.isEmpty() -> {
                CrmDrawerPlaceholder(
                    onOpenDrawer = { drawerScope.launch { drawerState.open() } },
                )
            }

            else -> {
                CrmContent(
                    onOpenDrawer = { drawerScope.launch { drawerState.open() } },
                    onSettings = onSettings,
                    onDesign = onDesign,
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
        CreateCrmDialog(
            templates = listUiState.templates,
            isCreating = listUiState.isCreating,
            onDismiss = { listViewModel.hideCreateDialog() },
            onCreate = { name, description, prefix, privacy, template ->
                listViewModel.createCrm(name, description, prefix, privacy, template)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrmDrawerPlaceholder(onOpenDrawer: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crm_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.crm_list_title))
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
                text = stringResource(R.string.crm_list_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllCrmsContent(
    onOpenDrawer: () -> Unit,
    onCrmClick: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    viewModel: CrmListViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            if (uiState.showSearch) {
                CrmSearchBar(
                    query = uiState.searchQuery,
                    placeholder = stringResource(R.string.crm_list_search_placeholder),
                    onQueryChange = viewModel::updateSearchQuery,
                    onClose = { viewModel.toggleSearch() }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.crm_all_crms)) },
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = stringResource(R.string.crm_list_title)
                            )
                        }
                    },
                    actions = {
                        NotificationBell(onClick = onOpenNotifications)
                        IconButton(onClick = { viewModel.toggleSearch() }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.crm_list_search)
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
                    uiState.isLoading && uiState.crm.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    uiState.error != null && uiState.crm.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = uiState.error!!.userMessage(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    else -> {
                        val filteredCrm = viewModel.filteredCrm()
                        if (filteredCrm.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 64.dp),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Text(
                                    text = if (uiState.searchQuery.isNotBlank()) {
                                        stringResource(R.string.crm_list_no_matching)
                                    } else {
                                        stringResource(R.string.crm_list_empty)
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(filteredCrm, key = { crm -> crm.fingerprint.ifEmpty { crm.id } }) { crm ->
                                    CrmRow(
                                        crm = crm,
                                        onClick = {
                                            onCrmClick(crm.fingerprint.ifEmpty { crm.id })
                                        },
                                        onUnsubscribe = { viewModel.unsubscribe(crm.id) }
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
 * Search field that takes over the whole top bar, as on the All CRMs list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrmSearchBar(
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
private fun CrmRow(
    crm: Crm,
    onClick: () -> Unit,
    onUnsubscribe: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showUnsubscribeConfirm by remember { mutableStateOf(false) }
    val crmId = crm.fingerprint.ifEmpty { crm.id }
    val canUnsubscribe = crm.owner != 1
    val unsubscribeTitle = stringResource(R.string.crm_settings_unsubscribe_title)
    val unsubscribeMessage = stringResource(R.string.crm_settings_unsubscribe_message)
    val unsubscribeLabel = stringResource(R.string.crm_settings_unsubscribe)
    val cancelLabel = stringResource(MochiR.string.common_cancel)
    val description = crm.description.takeIf { text -> text.isNotBlank() }

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
                seed = crmId.ifEmpty { crm.id },
                icon = Icons.Default.Folder
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = crm.name,
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
                        text = { Text(stringResource(R.string.crm_list_add_to_home)) },
                        leadingIcon = { Icon(Icons.Default.HomeMax, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            // mochi:/<entity> per claude/plans/mochi-uri-scheme.md.
                            val intent = Intent(Intent.ACTION_VIEW, "mochi:/$crmId".toUri()).apply {
                                setPackage(context.packageName)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                                putExtra("app", "crm")
                            }
                            val shortcut = ShortcutInfoCompat.Builder(context, "crm_$crmId")
                                .setShortLabel(crm.name)
                                .setLongLabel(crm.name)
                                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_crm))
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
private fun CrmContent(
    onOpenDrawer: () -> Unit,
    onSettings: (String) -> Unit,
    onDesign: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    initialObjectId: String? = null,
    viewModel: CrmViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showOverflow by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showAddColumn by remember { mutableStateOf(false) }

    LaunchedEffect(initialObjectId) {
        if (initialObjectId != null) {
            viewModel.selectObject(initialObjectId)
        }
    }

    val details = uiState.crmDetails
    val activeView = viewModel.getActiveView()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = details?.crm?.name ?: stringResource(R.string.crm_loading),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.crm_list_title))
                    }
                },
                actions = {
                    NotificationBell(onClick = onOpenNotifications)
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(
                            if (showSearch) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = stringResource(R.string.crm_search)
                        )
                    }
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.crm_more))
                        }
                        DropdownMenu(
                            expanded = showOverflow,
                            onDismissRequest = { showOverflow = false }
                        ) {
                            // Add column — only meaningful on a board view that
                            // groups by a field (mirrors web's overflow "Add
                            // column"). Creates a new option on the grouping field.
                            if (activeView?.viewtype == "board" && activeView.columns.isNotBlank()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.crm_board_add_column)) },
                                    onClick = {
                                        showOverflow = false
                                        showAddColumn = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.ViewColumn, contentDescription = null) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.crm_settings)) },
                                onClick = {
                                    showOverflow = false
                                    onSettings(viewModel.crmId)
                                },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.crm_design)) },
                                onClick = {
                                    showOverflow = false
                                    onDesign(viewModel.crmId)
                                },
                                leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (details != null) {
                FloatingActionButton(onClick = { viewModel.showCreateObjectDialog() }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.crm_create_object))
                }
            }
        }
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
                // View tabs
                if (details != null && details.views.isNotEmpty()) {
                    val views = details.views
                    val selectedIndex = views.indexOfFirst { it.id == uiState.activeViewId }.coerceAtLeast(0)
                    ScrollableTabRow(
                        selectedTabIndex = selectedIndex,
                        edgePadding = 16.dp
                    ) {
                        views.forEachIndexed { index, view ->
                            Tab(
                                selected = index == selectedIndex,
                                onClick = { viewModel.setActiveView(view.id) },
                                text = {
                                    Text(
                                        text = view.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                }

                // Search and filter bar
                if (showSearch) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = viewModel::updateSearchQuery,
                                placeholder = { Text(stringResource(R.string.crm_search_objects_placeholder)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            FilterChip(
                                selected = uiState.watchedOnly,
                                onClick = { viewModel.toggleWatchedOnly() },
                                label = { Text(stringResource(R.string.crm_watched)) },
                                leadingIcon = if (uiState.watchedOnly) {
                                    { Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                        if (activeView != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            SortRow(viewModel = viewModel)
                        }
                    }
                }

                // Main content
                when {
                    uiState.isLoading && details == null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    uiState.error is MochiError.NotFoundError && details == null -> {
                        NotFoundState(
                            title = stringResource(R.string.crm_not_found),
                            onBack = onOpenDrawer,
                        )
                    }

                    uiState.error != null && details == null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = uiState.error!!.userMessage(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    details != null -> {
                        val filteredObjects = viewModel.getFilteredObjects()
                        val allObjects = uiState.objects
                        when (activeView?.viewtype) {
                            "board" -> {
                                BoardView(
                                    objects = allObjects,
                                    view = activeView,
                                    viewModel = viewModel,
                                    onObjectClick = { viewModel.selectObject(it) },
                                    onCreateObject = { classId, title, initialValues ->
                                        viewModel.createObject(classId, title, initialValues = initialValues)
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
            fields = details.fields,
            options = details.options,
            people = uiState.people,
            objects = uiState.objects,
            presetParent = uiState.createObjectParent,
            isCreating = uiState.isCreatingObject,
            activeView = activeView,
            viewModel = viewModel,
            onDismiss = { viewModel.hideCreateObjectDialog() },
            onCreate = { classId, title, parent, initialValues, files ->
                viewModel.createObject(classId, title, parent, initialValues, files)
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
            crmId = viewModel.crmId,
            objectId = uiState.selectedObjectId!!,
            crmDetails = details,
            initialObject = uiState.objects.find { it.id == uiState.selectedObjectId },
            onDismiss = { viewModel.selectObject(null) },
            onObjectDeleted = {
                viewModel.selectObject(null)
                viewModel.refresh()
            },
            onNavigateToObject = { id -> viewModel.selectObject(id) },
            onAddChild = { parentId ->
                // Close the sheet, then open the create dialog with the
                // parent pre-selected. The dialog reads crm.hierarchy
                // and seeds the class to one that permits this parent.
                viewModel.selectObject(null)
                viewModel.showCreateObjectDialog(parent = parentId)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortRow(viewModel: CrmViewModel) {
    val activeField = viewModel.getActiveSortField()
    val activeDirection = viewModel.getActiveSortDirection()
    val customOptions = viewModel.getSortFieldOptions()

    val builtIn = listOf(
        "rank" to stringResource(R.string.crm_sort_rank),
        "number" to stringResource(R.string.crm_sort_number),
        "created" to stringResource(R.string.crm_sort_created),
        "updated" to stringResource(R.string.crm_sort_updated)
    )
    val all = customOptions + builtIn
    val activeLabel = all.firstOrNull { it.first == activeField }?.second
        ?: stringResource(R.string.crm_sort_rank)

    var expanded by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.crm_sort_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = activeLabel,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                if (customOptions.isNotEmpty()) {
                    customOptions.forEach { (id, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.setSortField(id)
                                expanded = false
                            }
                        )
                    }
                    HorizontalDivider()
                }
                builtIn.forEach { (id, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            viewModel.setSortField(id)
                            expanded = false
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = { viewModel.toggleSortDirection() }) {
            Icon(
                imageVector = if (activeDirection == "desc") Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                contentDescription = stringResource(
                    if (activeDirection == "desc") R.string.crm_sort_direction_desc
                    else R.string.crm_sort_direction_asc
                )
            )
        }
    }
}

// Add a new board column (= a new option on the board's grouping field).
// Name + a preset colour, mirroring web's OptionDialog used for "Add column".
// Boards render the option colour, not its icon, so no icon field here.
@Composable
private fun AddColumnDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, colour: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var colour by remember { mutableStateOf("#3b82f6") }
    val presetColours = listOf(
        "#ef4444", "#f97316", "#eab308", "#22c55e",
        "#06b6d4", "#3b82f6", "#8b5cf6", "#ec4899",
        "#6b7280", "#000000"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crm_board_add_column)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.crm_field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.crm_option_color), style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presetColours.take(5).forEach { hex ->
                        IconButton(onClick = { colour = hex }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Circle,
                                contentDescription = hex,
                                tint = parseColor(hex),
                                modifier = if (colour == hex) Modifier.size(28.dp) else Modifier.size(20.dp)
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presetColours.drop(5).forEach { hex ->
                        IconButton(onClick = { colour = hex }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Circle,
                                contentDescription = hex,
                                tint = parseColor(hex),
                                modifier = if (colour == hex) Modifier.size(28.dp) else Modifier.size(20.dp)
                            )
                        }
                    }
                }
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
