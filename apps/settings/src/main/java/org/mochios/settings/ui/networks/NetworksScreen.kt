// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.networks

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.util.NaturalCompare
import org.mochios.android.util.webUri
import org.mochios.settings.R
import org.mochios.settings.api.Handle
import org.mochios.settings.api.NetworkAccount
import org.mochios.settings.api.NetworkBlock
import org.mochios.settings.api.NetworkFeed
import org.mochios.settings.api.NetworkMute
import org.mochios.settings.api.NetworksData
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworksScreen(
    onBack: () -> Unit,
    viewModel: NetworksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val launch by viewModel.launch.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    // An error while content is on screen is shown over it rather than
    // replacing it.
    LaunchedEffect(state.error) {
        val failure = state.error
        if (failure != null && state.data != null) {
            snackbar.showSnackbar(failure.userMessage())
            viewModel.clearError()
        }
    }
    // The remote server's consent: only a web address may leave the app.
    LaunchedEffect(launch) {
        val url = launch ?: return@LaunchedEffect
        webUri(url)?.let { target ->
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        viewModel.consumeLaunch()
    }

    var linking by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf<Handle?>(null) }
    var releasing by remember { mutableStateOf<Handle?>(null) }
    var removing by remember { mutableStateOf<NetworkAccount?>(null) }
    var renaming by remember { mutableStateOf<NetworkAccount?>(null) }
    var aliasesFor by remember { mutableStateOf<NetworkAccount?>(null) }
    var labelersFor by remember { mutableStateOf<NetworkAccount?>(null) }
    var homeFor by remember { mutableStateOf<NetworkAccount?>(null) }
    var movingFor by remember { mutableStateOf<NetworkAccount?>(null) }
    var keysFor by remember { mutableStateOf<NetworkAccount?>(null) }
    var offering by remember { mutableStateOf(false) }
    var passwordFor by remember { mutableStateOf<NetworkAccount?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.networks_title)) },
                navigationIcon = {
                    MochiIconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val data = state.data
            when {
                data == null && state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                data == null -> state.error?.let { ErrorState(error = it, onRetry = { viewModel.refresh() }) }
                !data.offered -> Text(
                    stringResource(R.string.networks_not_offered),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                else -> NetworksContent(
                    data = data,
                    busy = state.busy,
                    check = viewModel::available,
                    onReserve = viewModel::reserve,
                    onLink = { linking = true },
                    onToggle = { handle, network, account ->
                        if (account != null) {
                            removing = account
                        } else {
                            viewModel.create(handle, network) { made ->
                                // A Bluesky account is offered its recovery key
                                // the moment it exists.
                                if (network == "bluesky" && made.id.isNotEmpty()) {
                                    offering = true
                                    keysFor = made
                                }
                            }
                        }
                    },
                    onAddName = { adding = it },
                    onArrive = { handle ->
                        viewModel.arrive(handle) { made -> if (made.id.isNotEmpty()) passwordFor = made }
                    },
                    onRelease = { releasing = it },
                    actions = AccountActions(
                        onRename = { renaming = it },
                        onPassword = { passwordFor = it },
                        onHome = { homeFor = it },
                        onKeys = { keysFor = it },
                        onAutomated = viewModel::toggleAutomated,
                        onAliases = { aliasesFor = it },
                        onMove = { movingFor = it },
                        onLabelers = { labelersFor = it },
                        onTest = viewModel::test,
                        onProfile = viewModel::toggleProfile,
                        onRemove = { removing = it },
                    ),
                    onBlock = viewModel::block,
                    onUnblock = viewModel::unblock,
                    onUnmute = viewModel::unmute,
                )
            }
        }
    }

    val data = state.data
    if (linking && data != null) {
        LinkDialog(
            providers = data.providers,
            busy = state.busy == "link",
            onLink = { type, fields, oauth -> viewModel.link(type, fields, oauth) { linking = false } },
            onDismiss = { linking = false },
        )
    }
    adding?.let { parent ->
        AddNameDialog(
            parent = parent,
            busy = state.busy == "name",
            check = viewModel::available,
            onAdd = { name -> viewModel.addName(parent, name) { adding = null } },
            onDismiss = { adding = null },
        )
    }
    renaming?.let { account ->
        ProfileDialog(
            account = account,
            busy = state.busy == "profile",
            onSave = { label -> viewModel.rename(account, label) { renaming = null } },
            onDismiss = { renaming = null },
        )
    }
    aliasesFor?.let { account ->
        ListDialog(
            title = stringResource(R.string.networks_aliases),
            initial = account.aliases,
            field = stringResource(R.string.networks_alias_field),
            placeholder = "@name@server.example",
            busy = state.busy == "aliases",
            onSave = { list, done -> viewModel.aliases(account, list, done) },
            onDismiss = { aliasesFor = null },
        )
    }
    labelersFor?.let { account ->
        ListDialog(
            title = stringResource(R.string.networks_labelers),
            initial = account.labelers,
            field = stringResource(R.string.networks_labeler),
            placeholder = "did:plc:...",
            busy = state.busy == "labelers",
            onSave = { list, done -> viewModel.labelers(account, list, done) },
            onDismiss = { labelersFor = null },
        )
    }
    homeFor?.let { account ->
        HomeDialog(
            account = account,
            feeds = data?.feeds.orEmpty(),
            busy = state.busy == "home",
            onSave = { choice -> viewModel.home(account, choice) { homeFor = null } },
            onDismiss = { homeFor = null },
        )
    }
    movingFor?.let { account ->
        MoveDialog(
            account = account,
            busy = state.busy == "move",
            onMove = { address -> viewModel.move(account, address) { movingFor = null } },
            onDismiss = { movingFor = null },
        )
    }
    keysFor?.let { account ->
        KeysDialog(
            account = account,
            offer = offering,
            busy = state.busy,
            load = viewModel::keys,
            onReplace = { done -> viewModel.replaceKeys(account, done) },
            onRecovery = { made -> viewModel.recovery(account, made) },
            onDismiss = {
                keysFor = null
                offering = false
            },
        )
    }
    passwordFor?.let { account ->
        PasswordDialog(
            account = account,
            busy = state.busy == "password",
            onMake = { device, made -> viewModel.password(account, device, made) },
            onDismiss = { passwordFor = null },
        )
    }
    releasing?.let { handle ->
        MochiAlertDialog(
            onDismissRequest = { releasing = null },
            title = stringResource(R.string.networks_release_title),
            text = stringResource(R.string.networks_release_message, handle.name),
            confirmText = stringResource(R.string.networks_release),
            onConfirm = {
                releasing = null
                viewModel.release(handle)
            },
            destructive = true,
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }
    removing?.let { account ->
        MochiAlertDialog(
            onDismissRequest = { removing = null },
            title = stringResource(if (account.hosted) R.string.networks_remove_title else R.string.networks_unlink_title),
            text = if (account.hosted) {
                stringResource(R.string.networks_remove_message, account.identifier)
            } else {
                stringResource(R.string.networks_unlink_message, account.identifier, account.server)
            },
            confirmText = stringResource(if (account.hosted) R.string.networks_remove else R.string.networks_unlink),
            onConfirm = {
                removing = null
                viewModel.remove(account)
            },
            destructive = true,
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }
}

/** What an account's menu does. */
internal class AccountActions(
    val onRename: (NetworkAccount) -> Unit = {},
    val onPassword: (NetworkAccount) -> Unit = {},
    val onHome: (NetworkAccount) -> Unit = {},
    val onKeys: (NetworkAccount) -> Unit = {},
    val onAutomated: (NetworkAccount) -> Unit = {},
    val onAliases: (NetworkAccount) -> Unit = {},
    val onMove: (NetworkAccount) -> Unit = {},
    val onLabelers: (NetworkAccount) -> Unit = {},
    val onTest: (NetworkAccount) -> Unit = {},
    val onProfile: (NetworkAccount) -> Unit = {},
    val onRemove: (NetworkAccount) -> Unit = {},
)

/** The screen's body once the server has said it offers networks. */
@Composable
internal fun NetworksContent(
    data: NetworksData,
    busy: String?,
    check: suspend (String, String) -> org.mochios.settings.api.Availability?,
    onReserve: (String, String, () -> Unit) -> Unit,
    onLink: () -> Unit,
    onToggle: (Handle, String, NetworkAccount?) -> Unit,
    onAddName: (Handle) -> Unit,
    onArrive: (Handle) -> Unit,
    onRelease: (Handle) -> Unit,
    actions: AccountActions,
    onBlock: (String, () -> Unit) -> Unit,
    onUnblock: (String) -> Unit,
    onUnmute: (String) -> Unit,
) {
    val parents = parentHandles(data.handles.handles)
    val accounts = data.accounts.sortedWith(compareBy(NaturalCompare) { it.identifier })
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (data.providers.isNotEmpty()) {
            item("link") {
                MochiOutlinedButton(onClick = onLink, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.networks_link))
                }
            }
        }
        if (mayReserve(data)) {
            item("chooser") {
                HandleChooser(
                    domains = data.domains,
                    suggestion = if (parents.isEmpty()) suggestHandle(data.name) else "",
                    title = stringResource(R.string.networks_choose),
                    reserving = busy == "reserve",
                    check = check,
                    onReserve = onReserve,
                )
            }
        }
        items(parents, key = { "handle:${it.domain}/${it.name}" }) { handle ->
            HandleCard(
                handle = handle,
                children = childHandles(data.handles.handles, handle),
                networks = data.networks,
                accounts = data.accounts,
                busy = busy,
                onToggle = onToggle,
                onAddName = onAddName,
                onArrive = onArrive,
                onRelease = onRelease,
            )
        }
        if (accounts.isNotEmpty()) {
            item("accounts") { SectionTitle(stringResource(R.string.networks_accounts)) }
            items(accounts, key = { "account:${it.id}" }) { account ->
                AccountCard(account = account, feeds = data.feeds, busy = busy == account.id, actions = actions)
            }
        }
        item("moderation") {
            ModerationSection(
                blocks = data.blocks,
                mutes = data.mutes,
                busy = busy,
                onBlock = onBlock,
                onUnblock = onUnblock,
                onUnmute = onUnmute,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 * One handle with the names under it: a switch per network, each naming the
 * address switching it on creates.
 */
@Composable
internal fun HandleCard(
    handle: Handle,
    children: List<Handle>,
    networks: List<String>,
    accounts: List<NetworkAccount>,
    busy: String?,
    onToggle: (Handle, String, NetworkAccount?) -> Unit,
    onAddName: (Handle) -> Unit,
    onArrive: (Handle) -> Unit,
    onRelease: (Handle) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    MochiCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(handle.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.networks_handle_on, handle.domain),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    MochiIconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreHoriz, contentDescription = stringResource(R.string.networks_handle_actions))
                    }
                    MochiDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        MochiDropdownMenuItem(
                            text = { Text(stringResource(R.string.networks_add_a_name)) },
                            onClick = { menu = false; onAddName(handle) },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                        )
                        if ("bluesky" in networks && hostedAccount(accounts, handle, "bluesky") == null) {
                            MochiDropdownMenuItem(
                                text = { Text(stringResource(R.string.networks_arrive)) },
                                onClick = { menu = false; onArrive(handle) },
                                leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                            )
                        }
                        MochiDropdownMenuItem(
                            text = { Text(stringResource(R.string.networks_release)) },
                            onClick = { menu = false; onRelease(handle) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        )
                    }
                }
            }
            for (row in listOf(handle) + children) {
                for (type in networks) {
                    val account = hostedAccount(accounts, row, type)
                    val network = networkLabel(type)
                    val switchLabel = stringResource(R.string.networks_network_for, network, row.name)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (row == handle) network else stringResource(R.string.networks_name_on, row.name, network),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                row.addresses[type].orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = account != null,
                            enabled = busy != "${row.name}/$type",
                            onCheckedChange = { onToggle(row, type, account) },
                            modifier = Modifier.semantics { contentDescription = switchLabel },
                        )
                    }
                }
            }
        }
    }
}

/** An account on another network, hosted here or linked on its own server. */
@Composable
internal fun AccountCard(
    account: NetworkAccount,
    feeds: List<NetworkFeed>,
    busy: Boolean,
    actions: AccountActions,
) {
    var menu by remember { mutableStateOf(false) }
    val (status, problem) = statusText(accountStatus(account, feeds))
    MochiCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(account.identifier, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                val name = account.label.ifEmpty { account.name }
                if (name.isNotEmpty()) {
                    Text(name, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    networkLabel(account.type) + " · " +
                        stringResource(if (account.hosted) R.string.networks_hosted else R.string.networks_linked_kind),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (status.isNotEmpty()) {
                    Text(
                        status,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (problem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                } else {
                    MochiIconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreHoriz, contentDescription = stringResource(R.string.networks_account_actions))
                    }
                }
                MochiDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    AccountMenu(account = account, actions = actions, close = { menu = false })
                }
            }
        }
    }
}

/** The account's menu, in the web's order. */
@Composable
private fun AccountMenu(account: NetworkAccount, actions: AccountActions, close: () -> Unit) {
    @Composable
    fun entry(text: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, run: (NetworkAccount) -> Unit) {
        MochiDropdownMenuItem(
            text = { Text(stringResource(text)) },
            onClick = { close(); run(account) },
            leadingIcon = { Icon(icon, contentDescription = null) },
        )
    }
    entry(R.string.networks_profile, Icons.Outlined.Edit, actions.onRename)
    if (account.hosted) {
        entry(R.string.networks_password_title, Icons.AutoMirrored.Filled.Login, actions.onPassword)
        if (homed(account)) entry(R.string.networks_home, Icons.Outlined.Home, actions.onHome)
        if (keyed(account)) entry(R.string.networks_keys, Icons.Outlined.Key, actions.onKeys)
        if (account.type == "fediverse") {
            entry(
                if (account.automated) R.string.networks_mark_person else R.string.networks_mark_automated,
                Icons.Outlined.SmartToy,
                actions.onAutomated,
            )
            entry(R.string.networks_aliases, Icons.Outlined.PersonAdd, actions.onAliases)
            if (account.moved.isEmpty()) entry(R.string.networks_move, Icons.Outlined.SwapHoriz, actions.onMove)
        }
        if (account.type == "bluesky") entry(R.string.networks_labelers, Icons.AutoMirrored.Outlined.Label, actions.onLabelers)
    } else {
        entry(R.string.networks_test, Icons.Outlined.Bolt, actions.onTest)
    }
    entry(
        if (account.profile) R.string.networks_profile_hide else R.string.networks_profile_show,
        if (account.profile) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
        actions.onProfile,
    )
    entry(R.string.networks_remove, Icons.Outlined.Delete, actions.onRemove)
}

/**
 * What the user has blocked and muted on other networks: a domain blocked
 * here, a person blocked or muted from their comment in feeds, each lifted
 * here.
 */
@Composable
internal fun ModerationSection(
    blocks: List<NetworkBlock>,
    mutes: List<NetworkMute>,
    busy: String?,
    onBlock: (String, () -> Unit) -> Unit,
    onUnblock: (String) -> Unit,
    onUnmute: (String) -> Unit,
) {
    var domain by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.networks_blocked_muted))
        val fieldLabel = stringResource(R.string.networks_block_domain)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MochiTextField(
                value = domain,
                onValueChange = { domain = it },
                placeholder = { Text("example.social") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.weight(1f).semantics { contentDescription = fieldLabel },
            )
            MochiOutlinedButton(
                onClick = { onBlock(domain) { domain = "" } },
                enabled = busy != "block" && domain.isNotBlank(),
            ) {
                if (busy == "block") {
                    CircularProgressIndicator(Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                }
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.networks_block))
            }
        }
        val rows = blocks.map { (it.domain.ifEmpty { it.actor }) to false } + mutes.map { it.actor to true }
        for ((target, muted) in rows) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(target, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(if (muted) R.string.networks_muted else R.string.networks_blocked),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MochiIconButton(
                    onClick = { if (muted) onUnmute(target) else onUnblock(target) },
                    enabled = busy != target,
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(if (muted) R.string.networks_unmute else R.string.networks_unblock, target),
                    )
                }
            }
        }
    }
}
