package org.mochios.wikis.ui.attachments

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.ConfirmDialog
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.LightboxScreen
import org.mochios.android.util.NaturalCompare
import org.mochios.wikis.R
import org.mochios.wikis.model.Attachment
import org.mochios.wikis.ui.components.LocalWikiContext
import org.mochios.wikis.ui.components.WikiContextValue
import org.mochios.android.R as MochiR

/**
 * Per-page attachments management screen. Mirrors web's
 * `apps/wikis/web/src/features/wiki/attachments-page.tsx`:
 *
 *  - TopAppBar with back arrow, "Attachments" title, and "N files (X images,
 *    Y documents)" subtitle.
 *  - Action row with an Upload button that launches the system file picker.
 *  - Sticky toolbar with search, filter dropdown, sort dropdown, and a
 *    grid/list view toggle.
 *  - Filtered + sorted body rendered as either a 3-column [LazyVerticalGrid]
 *    or a vertical [LazyColumn].
 *  - Tap an image to open it in the shared [LightboxScreen] (paginated
 *    through every image in the filtered list). Tap a document to enqueue
 *    it with [DownloadManager] — Android handles the rest.
 *  - Long-press a grid cell to surface a per-tile action menu (Copy embed /
 *    Delete) because hover doesn't exist on touch.
 *
 * Wraps the body in a [LocalWikiContext] provider so the markdown content
 * helpers and download-URL builders can resolve `${baseURL}attachments/<id>`
 * without each one having to be threaded a `serverUrl + wikiId` pair.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentsScreen(
    navController: NavController,
    viewModel: AttachmentsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Forward ViewModel events onto the snackbar host.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AttachmentsEvent.Toast -> snackbarHostState.showSnackbar(event.message)
                is AttachmentsEvent.Error -> snackbarHostState.showSnackbar(event.error.userMessage())
            }
        }
    }

    // System multi-file picker — same contract as InsertAttachmentDialog uses,
    // so consistent with the in-editor upload flow.
    val uploadFailedMsg = stringResource(R.string.wikis_attachments_upload_failed)
    val uploadingMsg = stringResource(R.string.wikis_attachments_uploading)
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.uploadAttachments(
                uris = uris,
                contentResolver = context.contentResolver,
                cacheDir = context.cacheDir,
                uploadFailed = uploadFailedMsg,
                uploadSuccess = uploadingMsg,
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AttachmentsTopBar(
                attachments = state.attachments,
                onBack = { navController.popBackStack() },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val wikiInfo = state.wiki
            when {
                state.isLoading && state.attachments.isEmpty() && wikiInfo == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                wikiInfo == null && state.error != null && state.attachments.isEmpty() -> {
                    ErrorRetry(message = state.error!!.userMessage(), onRetry = { viewModel.loadAttachments() })
                }
                wikiInfo == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    val wikiCtx = WikiContextValue(
                        wikiId = viewModel.wikiId,
                        info = wikiInfo,
                        permissions = state.permissions,
                        serverUrl = viewModel.serverUrl,
                    )
                    CompositionLocalProvider(LocalWikiContext provides wikiCtx) {
                        AttachmentsBody(
                            state = state,
                            baseURL = wikiCtx.baseURL,
                            token = viewModel.token,
                            onUpload = { filePicker.launch("*/*") },
                            onSearchChange = viewModel::setSearchQuery,
                            onClearSearch = viewModel::clearSearch,
                            onFilterChange = viewModel::setFilter,
                            onSortChange = viewModel::setSort,
                            onViewModeChange = viewModel::setViewMode,
                            onRefresh = viewModel::refresh,
                            onRequestDelete = viewModel::requestDelete,
                            onRetry = viewModel::loadAttachments,
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation
    val pending = state.pendingDelete
    if (pending != null) {
        val deleteSuccess = stringResource(R.string.wikis_attachments_delete_success)
        val deleteFailed = stringResource(R.string.wikis_attachments_delete_failed)
        ConfirmDialog(
            title = stringResource(R.string.wikis_attachments_delete_confirm_title),
            message = stringResource(
                R.string.wikis_attachments_delete_confirm_message,
                pending.name,
            ),
            confirmLabel = stringResource(MochiR.string.common_delete),
            isDestructive = true,
            onConfirm = { viewModel.confirmDelete(deleteSuccess, deleteFailed) },
            onDismiss = { viewModel.cancelDelete() },
        )
    }
}

/**
 * Top bar with back arrow, "Attachments" title and a localised subtitle of
 * the form "N files (X images, Y documents)". Uses [pluralStringResource]
 * for each count so single/plural English forms and other-language forms
 * resolve correctly per locale.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentsTopBar(
    attachments: List<Attachment>,
    onBack: () -> Unit,
) {
    val imageCount = attachments.count { isImage(it.type) }
    val docCount = attachments.size - imageCount
    val totalLabel = pluralStringResource(
        R.plurals.wikis_attachments_total_files, attachments.size, attachments.size,
    )
    val imagesLabel = pluralStringResource(
        R.plurals.wikis_attachments_total_images, imageCount, imageCount,
    )
    val docsLabel = pluralStringResource(
        R.plurals.wikis_attachments_total_documents, docCount, docCount,
    )
    val subtitle = "$totalLabel ($imagesLabel, $docsLabel)"

    TopAppBar(
        title = {
            Column {
                Text(
                    text = stringResource(R.string.wikis_attachments_title),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(MochiR.string.common_back),
                )
            }
        },
    )
}

/**
 * The main body once the wiki context is available. Holds the action row,
 * sticky toolbar, and the grid/list switch — plus the inline lightbox state
 * for tapping an image attachment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentsBody(
    state: AttachmentsUiState,
    baseURL: String,
    token: String?,
    onUpload: () -> Unit,
    onSearchChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onFilterChange: (AttachmentsFilter) -> Unit,
    onSortChange: (AttachmentsSort) -> Unit,
    onViewModeChange: (AttachmentsViewMode) -> Unit,
    onRefresh: () -> Unit,
    onRequestDelete: (Attachment) -> Unit,
    onRetry: () -> Unit,
) {
    val filtered = remember(state.attachments, state.searchQuery, state.filter, state.sort) {
        filterAndSort(state.attachments, state.searchQuery, state.filter, state.sort)
    }

    // Lightbox state — flipped open from a grid or list image tap. The
    // visible image set is the filtered images (so swiping skips files /
    // search-hidden images).
    val imageAttachments = remember(filtered) { filtered.filter { isImage(it.type) } }
    var lightboxIndex by remember { mutableStateOf<Int?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Upload action row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = onUpload,
                enabled = !state.isUploading,
            ) {
                if (state.isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Default.Upload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.wikis_attachments_upload))
            }
        }

        // Sticky toolbar (search + filter/sort/view-mode)
        AttachmentsToolbar(
            searchQuery = state.searchQuery,
            filter = state.filter,
            sort = state.sort,
            viewMode = state.viewMode,
            onSearchChange = onSearchChange,
            onClearSearch = onClearSearch,
            onFilterChange = onFilterChange,
            onSortChange = onSortChange,
            onViewModeChange = onViewModeChange,
        )

        HorizontalDivider()

        // Pull-to-refresh wraps the scrolling content.
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
        ) {
            when {
                state.error != null && state.attachments.isEmpty() -> {
                    ErrorRetry(message = state.error.userMessage(), onRetry = onRetry)
                }
                filtered.isEmpty() && state.attachments.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Default.Image,
                        title = stringResource(R.string.wikis_attachments_empty_title),
                        subtitle = stringResource(R.string.wikis_attachments_empty_description),
                        action = {
                            Button(onClick = onUpload, enabled = !state.isUploading) {
                                Icon(
                                    Icons.Default.Upload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.wikis_attachments_upload))
                            }
                        },
                    )
                }
                filtered.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Default.Search,
                        title = stringResource(R.string.wikis_attachments_no_results_title),
                        subtitle = stringResource(R.string.wikis_attachments_no_results_description),
                        action = {
                            TextButton(onClick = onClearSearch) {
                                Text(stringResource(R.string.wikis_attachments_search_clear))
                            }
                        },
                    )
                }
                state.viewMode == AttachmentsViewMode.GRID -> {
                    AttachmentsGrid(
                        attachments = filtered,
                        baseURL = baseURL,
                        token = token,
                        deletingId = state.deletingId,
                        onOpenImageAt = { id ->
                            val idx = imageAttachments.indexOfFirst { it.id == id }
                            if (idx >= 0) lightboxIndex = idx
                        },
                        onRequestDelete = onRequestDelete,
                    )
                }
                else -> {
                    AttachmentsList(
                        attachments = filtered,
                        baseURL = baseURL,
                        token = token,
                        deletingId = state.deletingId,
                        onOpenImageAt = { id ->
                            val idx = imageAttachments.indexOfFirst { it.id == id }
                            if (idx >= 0) lightboxIndex = idx
                        },
                        onRequestDelete = onRequestDelete,
                    )
                }
            }
        }
    }

    // Lightbox overlay
    val openIdx = lightboxIndex
    if (openIdx != null && imageAttachments.isNotEmpty()) {
        val urls = imageAttachments.map { "${baseURL}attachments/${it.id}" }
        LightboxScreen(
            images = urls,
            initialIndex = openIdx.coerceIn(0, urls.size - 1),
            onDismiss = { lightboxIndex = null },
        )
    }
}

/**
 * Search field, filter dropdown, sort dropdown, and grid/list toggle. Web's
 * equivalent flex-wraps everything onto a single line; on phones we keep the
 * search bar full-width on top and the three controls on a single row
 * underneath.
 */
@Composable
private fun AttachmentsToolbar(
    searchQuery: String,
    filter: AttachmentsFilter,
    sort: AttachmentsSort,
    viewMode: AttachmentsViewMode,
    onSearchChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onFilterChange: (AttachmentsFilter) -> Unit,
    onSortChange: (AttachmentsSort) -> Unit,
    onViewModeChange: (AttachmentsViewMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text(stringResource(R.string.wikis_attachments_search_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = onClearSearch) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.wikis_attachments_search_clear),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            } else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterMenu(
                filter = filter,
                onChange = onFilterChange,
                modifier = Modifier.weight(1f),
            )
            SortMenu(
                sort = sort,
                onChange = onSortChange,
                modifier = Modifier.weight(1f),
            )
            ViewModeToggle(
                viewMode = viewMode,
                onChange = onViewModeChange,
            )
        }
    }
}

@Composable
private fun FilterMenu(
    filter: AttachmentsFilter,
    onChange: (AttachmentsFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (filter) {
        AttachmentsFilter.ALL -> stringResource(R.string.wikis_attachments_filter_all)
        AttachmentsFilter.IMAGES -> stringResource(R.string.wikis_attachments_filter_images)
        AttachmentsFilter.DOCUMENTS -> stringResource(R.string.wikis_attachments_filter_documents)
    }

    Box(modifier = modifier) {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.wikis_attachments_filter_all)) },
                onClick = { onChange(AttachmentsFilter.ALL); expanded = false },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.wikis_attachments_filter_images)) },
                onClick = { onChange(AttachmentsFilter.IMAGES); expanded = false },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.wikis_attachments_filter_documents)) },
                onClick = { onChange(AttachmentsFilter.DOCUMENTS); expanded = false },
            )
        }
    }
}

@Composable
private fun SortMenu(
    sort: AttachmentsSort,
    onChange: (AttachmentsSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (sort) {
        AttachmentsSort.DATE -> stringResource(R.string.wikis_attachments_sort_date)
        AttachmentsSort.NAME -> stringResource(R.string.wikis_attachments_sort_name)
        AttachmentsSort.SIZE -> stringResource(R.string.wikis_attachments_sort_size)
    }
    Box(modifier = modifier) {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.wikis_attachments_sort_date)) },
                onClick = { onChange(AttachmentsSort.DATE); expanded = false },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.wikis_attachments_sort_name)) },
                onClick = { onChange(AttachmentsSort.NAME); expanded = false },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.wikis_attachments_sort_size)) },
                onClick = { onChange(AttachmentsSort.SIZE); expanded = false },
            )
        }
    }
}

@Composable
private fun ViewModeToggle(
    viewMode: AttachmentsViewMode,
    onChange: (AttachmentsViewMode) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onChange(AttachmentsViewMode.GRID) },
        ) {
            Icon(
                Icons.Default.GridView,
                contentDescription = stringResource(R.string.wikis_attachments_view_grid),
                tint = if (viewMode == AttachmentsViewMode.GRID) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        IconButton(
            onClick = { onChange(AttachmentsViewMode.LIST) },
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ViewList,
                contentDescription = stringResource(R.string.wikis_attachments_view_list),
                tint = if (viewMode == AttachmentsViewMode.LIST) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * 3-column grid view. Each cell is an [AttachmentGridCell] — tap to open the
 * lightbox (images) or enqueue a download (documents); long-press to surface
 * the per-tile Copy embed / Delete menu.
 */
@Composable
private fun AttachmentsGrid(
    attachments: List<Attachment>,
    baseURL: String,
    token: String?,
    deletingId: String?,
    onOpenImageAt: (String) -> Unit,
    onRequestDelete: (Attachment) -> Unit,
) {
    val context = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(attachments, key = { it.id }) { attachment ->
            AttachmentGridCell(
                attachment = attachment,
                baseURL = baseURL,
                isDeleting = deletingId == attachment.id,
                onTap = {
                    if (isImage(attachment.type)) {
                        onOpenImageAt(attachment.id)
                    } else {
                        startAttachmentDownload(
                            context = context,
                            url = "${baseURL}attachments/${attachment.id}",
                            name = attachment.name,
                            mimeType = attachment.type.ifBlank { "application/octet-stream" },
                            token = token,
                        )
                    }
                },
                onRequestDelete = { onRequestDelete(attachment) },
            )
        }
    }
}

/**
 * Vertical list view. Each row shows a thumbnail/icon, name, size · date,
 * and trailing icons for Open / Copy embed / Delete.
 */
@Composable
private fun AttachmentsList(
    attachments: List<Attachment>,
    baseURL: String,
    token: String?,
    deletingId: String?,
    onOpenImageAt: (String) -> Unit,
    onRequestDelete: (Attachment) -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        contentPadding = PaddingValues(vertical = 4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(attachments, key = { it.id }) { attachment ->
            AttachmentListRow(
                attachment = attachment,
                baseURL = baseURL,
                isDeleting = deletingId == attachment.id,
                onOpen = {
                    if (isImage(attachment.type)) {
                        onOpenImageAt(attachment.id)
                    } else {
                        startAttachmentDownload(
                            context = context,
                            url = "${baseURL}attachments/${attachment.id}",
                            name = attachment.name,
                            mimeType = attachment.type.ifBlank { "application/octet-stream" },
                            token = token,
                        )
                    }
                },
                onRequestDelete = { onRequestDelete(attachment) },
            )
            HorizontalDivider()
        }
    }
}

/**
 * A single grid cell: thumbnail (or file icon), name, size, and a small
 * actions menu surfaced via long-press. Tap opens (lightbox / download)
 * depending on type.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AttachmentGridCell(
    attachment: Attachment,
    baseURL: String,
    isDeleting: Boolean,
    onTap: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val copyLabel = stringResource(R.string.wikis_attachments_copy_embed)
    val deleteLabel = stringResource(R.string.wikis_attachments_delete)
    var menuOpen by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val format = LocalFormat.current

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onTap,
                onLongClick = { menuOpen = true },
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (isImage(attachment.type)) {
                    AsyncImage(
                        model = "${baseURL}attachments/${attachment.id}/thumbnail",
                        contentDescription = attachment.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = iconForType(attachment.type),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp),
                    )
                }
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(28.dp)
                            .align(Alignment.Center),
                    )
                }
            }
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    text = attachment.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = format.formatFileSize(attachment.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Long-press menu
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text(copyLabel) },
                    leadingIcon = {
                        Icon(
                            if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        val markdown = buildMarkdown(attachment)
                        clipboard.setText(AnnotatedString(markdown))
                        copied = true
                        menuOpen = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(deleteLabel) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onRequestDelete()
                    },
                )
            }
        }
    }
    // Reset copied indicator after a short window so a second copy gets the
    // same Check feedback.
    if (copied) {
        LaunchedEffect(copied) {
            kotlinx.coroutines.delay(1500)
            copied = false
        }
    }
}

/**
 * A single list row: thumbnail/icon on the left, name + "size · date" centre,
 * trailing icons for Open / Copy embed / Delete.
 */
@Composable
private fun AttachmentListRow(
    attachment: Attachment,
    baseURL: String,
    isDeleting: Boolean,
    onOpen: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val format = LocalFormat.current
    val createdLabel = if (attachment.created > 0) format.formatDate(attachment.created) else ""
    val sizeLabel = format.formatFileSize(attachment.size)
    var copied by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (isImage(attachment.type)) {
                AsyncImage(
                    model = "${baseURL}attachments/${attachment.id}/thumbnail",
                    contentDescription = attachment.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = iconForType(attachment.type),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (createdLabel.isNotEmpty()) "$sizeLabel · $createdLabel" else sizeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onOpen) {
            Icon(
                Icons.Default.Download,
                contentDescription = stringResource(R.string.wikis_attachments_open),
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(
            onClick = {
                clipboard.setText(AnnotatedString(buildMarkdown(attachment)))
                copied = true
            },
        ) {
            Icon(
                if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.wikis_attachments_copy_embed),
                modifier = Modifier.size(20.dp),
                tint = if (copied) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        IconButton(
            onClick = onRequestDelete,
            enabled = !isDeleting,
        ) {
            if (isDeleting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.wikis_attachments_delete),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (copied) {
        LaunchedEffect(copied) {
            kotlinx.coroutines.delay(1500)
            copied = false
        }
    }
}

@Composable
private fun ErrorRetry(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(MochiR.string.common_retry))
            }
        }
    }
}

// ----------------------------------------------------------------------------
// Helpers
// ----------------------------------------------------------------------------

/** Mirror of web's `isImage(type)` helper. */
private fun isImage(mime: String): Boolean = mime.startsWith("image/")

/**
 * Pick a sensible Material icon based on the attachment MIME type. Mirrors
 * the limited mapping web uses via `getFileIcon` — image / pdf / doc fall
 * through to the catch-all file icon when nothing else matches.
 */
private fun iconForType(mime: String): ImageVector {
    val lower = mime.lowercase()
    return when {
        lower.startsWith("image/") -> Icons.Default.Image
        lower.startsWith("video/") -> Icons.Default.Videocam
        lower.startsWith("audio/") -> Icons.Default.Audiotrack
        lower == "application/pdf" -> Icons.Default.PictureAsPdf
        lower.startsWith("text/") || lower.contains("document") || lower.contains("word") ->
            Icons.Default.Description
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}

/**
 * Build the markdown snippet for a copy-embed action. Mirrors the snippet
 * built by web's `handleCopy` — images become `![name](attachments/id)` and
 * everything else becomes `[name](attachments/id)`.
 */
private fun buildMarkdown(attachment: Attachment): String {
    val url = "attachments/${attachment.id}"
    return if (isImage(attachment.type)) {
        "![${attachment.name}]($url)"
    } else {
        "[${attachment.name}]($url)"
    }
}

/**
 * Apply the search filter, type filter, and sort-by from the toolbar to the
 * raw attachment list. Pure function so the screen can re-compute the
 * derived view with a single `remember` keyed on the inputs.
 */
private fun filterAndSort(
    attachments: List<Attachment>,
    searchQuery: String,
    filter: AttachmentsFilter,
    sort: AttachmentsSort,
): List<Attachment> {
    var result = attachments
    if (searchQuery.isNotBlank()) {
        val q = searchQuery.lowercase()
        result = result.filter { it.name.lowercase().contains(q) }
    }
    result = when (filter) {
        AttachmentsFilter.ALL -> result
        AttachmentsFilter.IMAGES -> result.filter { isImage(it.type) }
        AttachmentsFilter.DOCUMENTS -> result.filter { !isImage(it.type) }
    }
    return when (sort) {
        AttachmentsSort.DATE -> result.sortedByDescending { it.created }
        AttachmentsSort.NAME -> result.sortedWith(compareBy(NaturalCompare) { it.name })
        AttachmentsSort.SIZE -> result.sortedByDescending { it.size }
    }
}

/**
 * Hand a non-image attachment off to Android's [DownloadManager]. Notification
 * is shown on completion; the Authorization header is set inline so the
 * server's wikis auth middleware accepts the request even though
 * DownloadManager runs outside the app process and has no access to the
 * normal interceptor stack.
 */
private fun startAttachmentDownload(
    context: Context,
    url: String,
    name: String,
    mimeType: String,
    token: String?,
): Long {
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val req = DownloadManager.Request(Uri.parse(url))
        .setTitle(name)
        .setDescription(context.getString(R.string.wikis_attachments_downloading))
        .setMimeType(mimeType.ifBlank { "application/octet-stream" })
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, sanitiseFilename(name))
    if (!token.isNullOrBlank()) {
        req.addRequestHeader("Authorization", "Bearer $token")
    }
    return dm.enqueue(req)
}

/**
 * DownloadManager rejects names containing path separators or NULs. Replace
 * anything unsafe with an underscore so the destination filename always
 * lands directly under DIRECTORY_DOWNLOADS.
 */
private fun sanitiseFilename(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "download"
    return trimmed.replace(Regex("[/\\\\ ]"), "_")
}
