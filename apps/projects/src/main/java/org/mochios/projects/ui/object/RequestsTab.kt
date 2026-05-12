package org.mochios.projects.ui.`object`

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.projects.R
import org.mochios.projects.model.Branch
import org.mochios.projects.model.MergeCheck
import org.mochios.projects.model.MergeRequest
import org.mochios.projects.model.Repository
import org.mochios.android.R as MochiR

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RequestsTab(
    requests: List<MergeRequest>,
    projectId: String,
    viewModel: ObjectDetailViewModel,
    onViewDiff: (String, String, String, String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedRequest by remember { mutableStateOf<MergeRequest?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (requests.isEmpty() && selectedRequest == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.projects_request_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (selectedRequest != null) {
            RequestDetailView(
                request = selectedRequest!!,
                projectId = projectId,
                viewModel = viewModel,
                onBack = { selectedRequest = null },
                onViewDiff = onViewDiff
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(requests, key = { it.id }) { request ->
                    RequestItem(
                        request = request,
                        onClick = { selectedRequest = request },
                        onDelete = { viewModel.deleteRequest(request.id) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.projects_request_new))
        }
    }

    if (showCreateDialog) {
        CreateRequestDialog(
            viewModel = viewModel,
            onDismiss = { showCreateDialog = false },
            onCreate = { repo, source, target, title, description, draft ->
                viewModel.createRequest(repo, source, target, title, description, draft)
                showCreateDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RequestItem(
    request: MergeRequest,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showOverflow by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = request.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusChip(status = request.status, draft = request.draft)
                Text(
                    text = "${request.source} -> ${request.target}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Box {
            IconButton(onClick = { showOverflow = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(MochiR.string.common_more_options))
            }
            DropdownMenu(
                expanded = showOverflow,
                onDismissRequest = { showOverflow = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(MochiR.string.common_delete)) },
                    onClick = {
                        showOverflow = false
                        onDelete()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    }
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: String, draft: Boolean) {
    val (label, color) = when {
        draft -> stringResource(R.string.projects_request_status_draft) to MaterialTheme.colorScheme.outlineVariant
        status == "open" -> stringResource(R.string.projects_request_status_open) to Color(0xFF4CAF50)
        status == "merged" -> stringResource(R.string.projects_request_status_merged) to Color(0xFF9C27B0)
        status == "closed" -> stringResource(R.string.projects_request_status_closed) to Color(0xFFF44336)
        else -> status.replaceFirstChar { it.uppercase() } to MaterialTheme.colorScheme.outlineVariant
    }
    SuggestionChip(
        onClick = { },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = color.copy(alpha = 0.15f)
        )
    )
}

@Composable
private fun RequestDetailView(
    request: MergeRequest,
    projectId: String,
    viewModel: ObjectDetailViewModel,
    onBack: () -> Unit,
    onViewDiff: (String, String, String, String) -> Unit
) {
    val mergeCheck by viewModel.mergeCheck.collectAsState()
    val isCheckingMerge by viewModel.isCheckingMerge.collectAsState()
    val mergeSuccess by viewModel.mergeSuccess.collectAsState()
    var showMergeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(request.id) {
        viewModel.clearMergeState()
        if (request.status == "open" && !request.draft) {
            viewModel.checkMerge(request.repository, request.source, request.target)
        }
    }

    LaunchedEffect(mergeSuccess) {
        if (mergeSuccess) {
            onBack()
            viewModel.clearMergeState()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.projects_request_back_to_list))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = request.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        StatusChip(status = request.status, draft = request.draft)

        Spacer(modifier = Modifier.height(12.dp))

        if (request.description.isNotBlank()) {
            Text(
                text = request.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.projects_request_repository, request.repository), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.projects_request_source, request.source), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.projects_request_target, request.target), style = MaterialTheme.typography.bodySmall)
            }
        }

        // Merge check status
        if (request.status == "open" && !request.draft) {
            Spacer(modifier = Modifier.height(12.dp))
            when {
                isCheckingMerge -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.projects_request_checking_merge), style = MaterialTheme.typography.bodySmall)
                    }
                }
                mergeCheck != null -> {
                    val check = mergeCheck!!
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (check.canMerge) "\u2705" else "\u274C",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (check.canMerge) stringResource(R.string.projects_request_can_merge) else stringResource(R.string.projects_request_cannot_merge),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (check.canMerge) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                        )
                    }
                    if (check.ahead > 0 || check.behind > 0) {
                        Text(
                            text = stringResource(R.string.projects_request_ahead_behind, check.ahead, check.behind),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Conflict list
                    if (check.conflicts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = pluralStringResource(R.plurals.projects_request_conflicts, check.conflicts.size, check.conflicts.size),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        check.conflicts.forEach { file ->
                            Text(
                                text = file,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        if (request.draft && request.status == "open") {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.projects_request_draft_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                onViewDiff(projectId, request.repository, request.source, request.target)
            }) {
                Text(stringResource(R.string.projects_request_view_diff))
            }

            if (request.status == "open" && !request.draft && mergeCheck?.canMerge == true) {
                TextButton(onClick = { showMergeDialog = true }) {
                    Text(stringResource(R.string.projects_request_merge), color = Color(0xFF4CAF50))
                }
            }

            if (request.status == "open" && request.draft) {
                TextButton(onClick = {
                    viewModel.updateRequest(request.id, null, null, null, false)
                }) {
                    Text(stringResource(R.string.projects_request_mark_ready))
                }
            }

            if (request.status == "open") {
                TextButton(onClick = {
                    viewModel.updateRequest(request.id, null, null, "closed", null)
                }) {
                    Text(stringResource(R.string.projects_request_close))
                }
            }
        }
    }

    if (showMergeDialog) {
        MergeDialog(
            request = request,
            onDismiss = { showMergeDialog = false },
            onMerge = { message, method ->
                showMergeDialog = false
                viewModel.performMerge(
                    request.repository, request.source, request.target,
                    message, method, request.id
                )
            }
        )
    }
}

@Composable
private fun MergeDialog(
    request: MergeRequest,
    onDismiss: () -> Unit,
    onMerge: (message: String, method: String) -> Unit
) {
    val defaultMessage = stringResource(R.string.projects_request_merge_default_message, request.source, request.target)
    var message by remember(defaultMessage) { mutableStateOf(defaultMessage) }
    var method by remember { mutableStateOf("merge") }

    val methodLabels = mapOf(
        "merge" to stringResource(R.string.projects_request_method_merge),
        "squash" to stringResource(R.string.projects_request_method_squash),
        "rebase" to stringResource(R.string.projects_request_method_rebase)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.projects_request_merge_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text(stringResource(R.string.projects_request_commit_message)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.projects_request_method), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("merge", "squash", "rebase").forEach { m ->
                        SuggestionChip(
                            onClick = { method = m },
                            label = { Text(methodLabels[m] ?: m) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (method == m) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onMerge(message, method) },
                enabled = message.isNotBlank()
            ) {
                Text(stringResource(R.string.projects_request_merge))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MochiR.string.common_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateRequestDialog(
    viewModel: ObjectDetailViewModel,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String, String?, Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf(false) }
    var selectedRepo by remember { mutableStateOf<Repository?>(null) }
    var selectedSource by remember { mutableStateOf<Branch?>(null) }
    var selectedTarget by remember { mutableStateOf<Branch?>(null) }
    var repositories by remember { mutableStateOf<List<Repository>>(emptyList()) }
    var branches by remember { mutableStateOf<List<Branch>>(emptyList()) }
    var isLoadingBranches by remember { mutableStateOf(false) }

    // Load repositories on open
    LaunchedEffect(Unit) {
        repositories = viewModel.loadRepositories()
    }

    // Load branches when repo changes; reset both branch picks
    LaunchedEffect(selectedRepo?.id) {
        selectedSource = null
        selectedTarget = null
        val repo = selectedRepo
        if (repo == null) {
            branches = emptyList()
            return@LaunchedEffect
        }
        isLoadingBranches = true
        branches = viewModel.loadBranches(repo.id)
        isLoadingBranches = false
        // Default target to repository's default branch if available
        selectedTarget = branches.firstOrNull { it.isDefault }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.projects_request_create_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                RepositoryDropdown(
                    repositories = repositories,
                    selected = selectedRepo,
                    onSelect = { selectedRepo = it }
                )
                Spacer(modifier = Modifier.height(8.dp))
                BranchDropdown(
                    label = stringResource(R.string.projects_request_field_source),
                    branches = branches,
                    selected = selectedSource,
                    enabled = selectedRepo != null && !isLoadingBranches,
                    onSelect = { selectedSource = it }
                )
                Spacer(modifier = Modifier.height(8.dp))
                BranchDropdown(
                    label = stringResource(R.string.projects_request_field_target),
                    branches = branches,
                    selected = selectedTarget,
                    enabled = selectedRepo != null && !isLoadingBranches,
                    onSelect = { selectedTarget = it }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.projects_request_field_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.projects_request_field_description)) },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = draft,
                        onCheckedChange = { draft = it }
                    )
                    Text(stringResource(R.string.projects_request_field_draft))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val repo = selectedRepo ?: return@TextButton
                    val source = selectedSource ?: return@TextButton
                    val target = selectedTarget ?: return@TextButton
                    onCreate(
                        repo.id,
                        source.name,
                        target.name,
                        title,
                        description.ifBlank { null },
                        draft
                    )
                },
                enabled = title.isNotBlank() && selectedRepo != null &&
                    selectedSource != null && selectedTarget != null
            ) {
                Text(stringResource(R.string.projects_request_create_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MochiR.string.common_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepositoryDropdown(
    repositories: List<Repository>,
    selected: Repository?,
    onSelect: (Repository) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected?.name ?: "",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.projects_request_field_repository)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            repositories.forEach { repo ->
                DropdownMenuItem(
                    text = { Text(repo.name) },
                    onClick = {
                        onSelect(repo)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BranchDropdown(
    label: String,
    branches: List<Branch>,
    selected: Branch?,
    enabled: Boolean,
    onSelect: (Branch) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it }
    ) {
        OutlinedTextField(
            value = selected?.name ?: "",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        DropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false }
        ) {
            branches.forEach { branch ->
                DropdownMenuItem(
                    text = { Text(branch.name) },
                    onClick = {
                        onSelect(branch)
                        expanded = false
                    }
                )
            }
        }
    }
}
