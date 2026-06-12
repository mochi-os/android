package org.mochios.settings.ui.tokens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.settings.R
import org.mochios.settings.api.ApiToken
import org.mochios.settings.ui.login.StepUpHost
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokensScreen(
    onBack: () -> Unit,
    viewModel: TokensViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val newToken by viewModel.newApiToken.collectAsState()
    val context = LocalContext.current
    var showCreate by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tokens_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.tokens_create))
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null -> Text(
                    text = state.error!!.userMessage(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
                state.tokens.isEmpty() -> Text(
                    text = stringResource(R.string.tokens_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.tokens, key = { it.hash }) { token ->
                        TokenRow(token = token, onDelete = { viewModel.delete(token.hash) })
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateTokenDialog(
            onCancel = { showCreate = false },
            onConfirm = { name ->
                showCreate = false
                viewModel.create(name)
            },
        )
    }

    newToken?.let { token ->
        NewTokenDialog(
            token = token,
            onCopy = { copyToClipboard(context, "api token", token) },
            onDone = viewModel::acknowledgeNewToken,
        )
    }

    StepUpHost(viewModel.stepUp)
}

@Composable
private fun TokenRow(token: ApiToken, onDelete: () -> Unit) {
    val format = LocalFormat.current
    var confirm by remember(token.hash) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(token.name, fontWeight = FontWeight.SemiBold)
                if (token.created > 0) {
                    Text(
                        stringResource(R.string.account_created_at, format.formatDateTime(token.created)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val never = stringResource(R.string.tokens_never)
                Text(
                    stringResource(
                        R.string.account_last_used,
                        if (token.lastUsed > 0) format.formatRelativeTime(token.lastUsed) else never,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(
                        R.string.tokens_expires,
                        if (token.expires > 0) format.formatRelativeTime(token.expires) else never,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (token.scopes.isNotEmpty()) {
                    Text(
                        stringResource(R.string.tokens_scopes, token.scopes.joinToString(", ")),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { confirm = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.account_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.tokens_delete_title)) },
            text = { Text(stringResource(R.string.tokens_delete_message, token.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onDelete()
                }) { Text(stringResource(R.string.account_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) {
                    Text(stringResource(R.string.account_cancel))
                }
            },
        )
    }
}

@Composable
private fun CreateTokenDialog(onCancel: () -> Unit, onConfirm: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.tokens_create_title)) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text(stringResource(R.string.tokens_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val n = draft.trim()
                    if (n.isNotBlank()) onConfirm(n)
                },
                enabled = draft.trim().isNotEmpty(),
            ) { Text(stringResource(R.string.tokens_create_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.account_cancel)) }
        },
    )
}

@Composable
private fun NewTokenDialog(token: String, onCopy: () -> Unit, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.tokens_new_title)) },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(10.dp),
                ) {
                    Text(
                        stringResource(R.string.tokens_new_warning),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = token,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCopy) { Text(stringResource(R.string.account_copy)) }
        },
        dismissButton = {
            TextButton(onClick = onDone) { Text(stringResource(R.string.account_done)) }
        },
    )
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cb.setPrimaryClip(ClipData.newPlainText(label, value))
}
