package org.mochios.settings.ui.domains

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.settings.R
import org.mochios.android.R as MochiR
import org.mochios.settings.api.Delegation
import org.mochios.settings.api.Domain
import org.mochios.settings.api.DomainDetailsData
import org.mochios.settings.api.Route

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomainsScreen(
    onBack: () -> Unit,
    viewModel: DomainsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showAddDomain by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.domain_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (state.isAdmin) {
                        IconButton(onClick = { showAddDomain = true }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.domain_add))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null && state.domains.isEmpty() -> Text(
                    text = state.error!!.userMessage(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
                state.domains.isEmpty() -> Text(
                    text = stringResource(
                        if (state.isAdmin) R.string.domain_empty_admin else R.string.domain_empty_user,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.domains, key = { it.domain }) { domain ->
                        DomainCard(
                            domain = domain,
                            details = state.details[domain.domain],
                            isLoadingDetails = state.loadingDetails.contains(domain.domain),
                            isAdmin = state.isAdmin,
                            apps = state.apps,
                            entities = state.entities,
                            userResults = state.userResults,
                            onExpand = { viewModel.loadDetails(domain.domain) },
                            onLoadRouteTargets = { viewModel.loadRouteTargets() },
                            onUserSearch = { viewModel.searchUsers(it) },
                            onClearUserResults = { viewModel.clearUserResults() },
                            onVerify = { viewModel.verifyDomain(domain.domain) },
                            onTls = { viewModel.setTls(domain.domain, it) },
                            onDelete = { viewModel.deleteDomain(domain.domain) },
                            onRouteCreate = { path, method, target, priority ->
                                viewModel.createRoute(domain.domain, path, method, target, priority)
                            },
                            onRouteUpdate = { path, method, target, priority, enabled ->
                                viewModel.updateRoute(domain.domain, path, method, target, priority, enabled)
                            },
                            onRouteDelete = { path -> viewModel.deleteRoute(domain.domain, path) },
                            onDelegationCreate = { path, owner ->
                                viewModel.createDelegation(domain.domain, path, owner)
                            },
                            onDelegationDelete = { path, owner ->
                                viewModel.deleteDelegation(domain.domain, path, owner)
                            },
                        )
                    }
                }
            }
        }
    }

    if (showAddDomain) {
        AddDomainDialog(
            onDismiss = { showAddDomain = false },
            onConfirm = { name ->
                viewModel.createDomain(name)
                showAddDomain = false
            },
        )
    }
}

@Composable
private fun DomainCard(
    domain: Domain,
    details: DomainDetailsData?,
    isLoadingDetails: Boolean,
    isAdmin: Boolean,
    apps: List<org.mochios.settings.api.RouteApp>,
    entities: List<org.mochios.settings.api.RouteEntity>,
    userResults: List<org.mochios.settings.api.UserSearchResult>,
    onExpand: () -> Unit,
    onLoadRouteTargets: () -> Unit,
    onUserSearch: (String) -> Unit,
    onClearUserResults: () -> Unit,
    onVerify: () -> Unit,
    onTls: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onRouteCreate: (path: String, method: String, target: String, priority: Int) -> Unit,
    onRouteUpdate: (path: String, method: String, target: String, priority: Int, enabled: Boolean) -> Unit,
    onRouteDelete: (path: String) -> Unit,
    onDelegationCreate: (path: String, owner: String) -> Unit,
    onDelegationDelete: (path: String, owner: String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteDomain by remember { mutableStateOf(false) }
    var showAddRoute by remember { mutableStateOf(false) }
    var editRoute by remember { mutableStateOf<Route?>(null) }
    var showAddDelegation by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = domain.domain,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val statusRes = if (domain.verified == 1) R.string.domain_status_verified
                    else R.string.domain_status_pending
                    Text(
                        text = stringResource(statusRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (domain.verified == 1) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = {
                    if (!expanded) onExpand()
                    expanded = !expanded
                }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                if (isLoadingDetails && details == null) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                } else if (details != null) {
                    // DNS verification hint
                    if (domain.verified == 0 && domain.token.isNotBlank()) {
                        VerificationBlock(domain = domain, onVerify = onVerify)
                        Spacer(Modifier.height(12.dp))
                    }

                    if (isAdmin) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.domain_https),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Switch(checked = domain.tls == 1, onCheckedChange = onTls)
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                    }

                    // Routes section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.route_section),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = { onLoadRouteTargets(); showAddRoute = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.route_add))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    if (details.routes.isEmpty()) {
                        Text(
                            stringResource(R.string.route_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        details.routes.forEach { route ->
                            RouteRow(
                                route = route,
                                onEdit = { onLoadRouteTargets(); editRoute = route },
                                onDelete = { onRouteDelete(route.path) },
                            )
                        }
                    }

                    // Delegations section (admin only)
                    if (isAdmin) {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.domain_delegations),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(onClick = { showAddDelegation = true }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.domain_delegation_add))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        if (details.delegations.isEmpty()) {
                            Text(
                                stringResource(R.string.domain_delegations_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        } else {
                            details.delegations.forEach { delegation ->
                                DelegationRow(
                                    delegation = delegation,
                                    onDelete = { onDelegationDelete(delegation.path, delegation.owner) },
                                )
                            }
                        }

                        // Delete domain
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { showDeleteDomain = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.domain_delete))
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDomain) {
        ConfirmDialog(
            title = stringResource(R.string.domain_delete_title),
            message = stringResource(R.string.domain_delete_message, domain.domain),
            confirmText = stringResource(R.string.domain_delete),
            onConfirm = {
                showDeleteDomain = false
                onDelete()
            },
            onDismiss = { showDeleteDomain = false },
        )
    }
    if (showAddRoute) {
        RouteDialog(
            initial = null,
            apps = apps,
            entities = entities,
            onDismiss = { showAddRoute = false },
            onConfirm = { path, method, target, priority, _ ->
                showAddRoute = false
                onRouteCreate(path, method, target, priority)
            },
        )
    }
    editRoute?.let { route ->
        RouteDialog(
            initial = route,
            apps = apps,
            entities = entities,
            onDismiss = { editRoute = null },
            onConfirm = { _, method, target, priority, enabled ->
                editRoute = null
                onRouteUpdate(route.path, method, target, priority, enabled)
            },
        )
    }
    if (showAddDelegation) {
        DelegationDialog(
            userResults = userResults,
            onSearch = onUserSearch,
            onDismiss = { onClearUserResults(); showAddDelegation = false },
            onConfirm = { path, owner ->
                onClearUserResults()
                showAddDelegation = false
                onDelegationCreate(path, owner)
            },
        )
    }
}

@Composable
private fun VerificationBlock(domain: Domain, onVerify: () -> Unit) {
    Card(colors = CardDefaults.outlinedCardColors()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = stringResource(R.string.domain_dns_hint),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "_mochi-verify.${domain.domain}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "mochi-verify=${domain.token}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = onVerify) {
                Text(stringResource(R.string.domain_verify))
            }
        }
    }
}

@Composable
private fun RouteRow(route: Route, onEdit: () -> Unit, onDelete: () -> Unit) {
    var confirm by remember(route.path) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = route.path.ifBlank { "/" },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "${route.method} → ${route.targetName ?: route.target}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (route.priority != 0 || route.enabled == 0) {
                Text(
                    text = stringResource(
                        R.string.route_meta,
                        route.priority,
                        stringResource(if (route.enabled == 1) R.string.route_enabled else R.string.route_disabled),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.route_edit))
        }
        IconButton(onClick = { confirm = true }) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.route_delete),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = stringResource(R.string.route_delete_title),
            message = stringResource(R.string.route_delete_message, route.path.ifBlank { "/" }),
            confirmText = stringResource(R.string.route_delete),
            onConfirm = {
                confirm = false
                onDelete()
            },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
private fun DelegationRow(delegation: Delegation, onDelete: () -> Unit) {
    var confirm by remember(delegation.id) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = delegation.username,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = delegation.path.ifBlank { "/" },
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { confirm = true }) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.domain_delegation_delete),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = stringResource(R.string.domain_delegation_delete_title),
            message = stringResource(R.string.domain_delegation_delete_message, delegation.username),
            confirmText = stringResource(R.string.domain_delegation_delete),
            onConfirm = {
                confirm = false
                onDelete()
            },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
private fun AddDomainDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.domain_add_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.domain_name)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.domain_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(MochiR.string.common_cancel)) }
        },
    )
}

@Composable
private fun RouteDialog(
    initial: Route?,
    apps: List<org.mochios.settings.api.RouteApp>,
    entities: List<org.mochios.settings.api.RouteEntity>,
    onDismiss: () -> Unit,
    onConfirm: (path: String, method: String, target: String, priority: Int, enabled: Boolean) -> Unit,
) {
    var path by remember { mutableStateOf(initial?.path ?: "") }
    var method by remember { mutableStateOf(initial?.method ?: "app") }
    var target by remember { mutableStateOf(initial?.target ?: "") }
    var priority by remember { mutableStateOf((initial?.priority ?: 0).toString()) }
    var enabled by remember { mutableStateOf((initial?.enabled ?: 1) == 1) }
    val editing = initial != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (editing) R.string.route_edit_title else R.string.route_add_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    singleLine = true,
                    enabled = !editing,
                    label = { Text(stringResource(R.string.route_path)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                MethodPicker(method, onChange = { method = it; target = "" })
                Spacer(Modifier.height(8.dp))
                // App and entity targets pick from the server-provided lists so
                // the user never types a raw id; redirect targets stay free-text
                // (an arbitrary URL).
                when (method) {
                    "app" -> TargetPicker(
                        label = stringResource(R.string.route_target_app),
                        selectedLabel = apps.firstOrNull { it.id == target }?.name,
                        options = apps.map { it.id to it.name },
                        onSelect = { target = it },
                    )
                    "entity" -> TargetPicker(
                        label = stringResource(R.string.route_target_entity),
                        selectedLabel = entities.firstOrNull { it.id == target }?.name,
                        options = entities.map { it.id to it.name },
                        onSelect = { target = it },
                    )
                    else -> OutlinedTextField(
                        value = target,
                        onValueChange = { target = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.route_target_url)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = priority,
                    onValueChange = { priority = it.filter(Char::isDigit) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.route_priority)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (editing) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.route_enabled), modifier = Modifier.weight(1f))
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(path.trim(), method, target.trim(), priority.toIntOrNull() ?: 0, enabled)
                },
                enabled = target.isNotBlank(),
            ) { Text(stringResource(if (editing) R.string.route_save else R.string.route_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(MochiR.string.common_cancel)) }
        },
    )
}

@Composable
private fun MethodPicker(value: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val labelRes = when (value) {
        "entity" -> R.string.route_method_entity
        "redirect" -> R.string.route_method_redirect
        else -> R.string.route_method_app
    }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.route_method) + ": " + stringResource(labelRes))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.route_method_app)) },
                onClick = { onChange("app"); open = false },
                trailingIcon = { if (value == "app") Icon(Icons.Default.Check, null) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.route_method_entity)) },
                onClick = { onChange("entity"); open = false },
                trailingIcon = { if (value == "entity") Icon(Icons.Default.Check, null) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.route_method_redirect)) },
                onClick = { onChange("redirect"); open = false },
                trailingIcon = { if (value == "redirect") Icon(Icons.Default.Check, null) },
            )
        }
    }
}

/**
 * A dropdown that selects a route target from a server-provided list (apps or
 * entities), so the user picks a name rather than typing a raw id. Falls back to
 * a disabled-looking prompt when nothing is selected.
 */
@Composable
private fun TargetPicker(
    label: String,
    selectedLabel: String?,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(label + ": " + (selectedLabel ?: stringResource(R.string.route_target_select)))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (options.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.route_target_none)) },
                    onClick = { open = false },
                    enabled = false,
                )
            }
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onSelect(id); open = false },
                    trailingIcon = { if (selectedLabel == name) Icon(Icons.Default.Check, null) },
                )
            }
        }
    }
}

@Composable
private fun DelegationDialog(
    userResults: List<org.mochios.settings.api.UserSearchResult>,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (path: String, owner: String) -> Unit,
) {
    var path by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    // The picked user: null until a search result is tapped. Selecting clears the
    // dropdown; editing the query again re-opens the search.
    var selected by remember { mutableStateOf<org.mochios.settings.api.UserSearchResult?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.domain_delegation_add_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        selected = null
                        onSearch(it)
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.domain_delegation_user)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                // Show matches while the user is typing and hasn't picked one yet.
                if (selected == null && userResults.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    userResults.forEach { user ->
                        TextButton(
                            onClick = {
                                selected = user
                                query = user.username
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = user.username,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.domain_delegation_path)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val uid = selected?.uid ?: return@TextButton
                    onConfirm(path.trim(), uid)
                },
                enabled = selected != null,
            ) { Text(stringResource(R.string.domain_delegation_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(MochiR.string.common_cancel)) }
        },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(MochiR.string.common_cancel)) }
        },
    )
}
