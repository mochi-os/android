package org.mochios.staff.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.LoadingState
import org.mochios.staff.R

/**
 * Staff Configuration screen (admin only).
 *
 * Mirrors web's `apps/staff/web/src/features/config/config-page.tsx`:
 *
 *  - Four logical sections: Moderation, Payments, Reviews, Stripe.
 *  - Each field is a labelled input with an optional suffix; the Save button
 *    appears beside the field only when the local value diverges from the
 *    server value.
 *  - Stripe secret key uses a password-masked text field.
 *
 * Admin gating lives at the route level in [org.mochios.staff.navigation.staffNavGraph]
 * (mirroring web's `beforeLoad` redirect on `/_authenticated/config`): non-admins
 * are silently redirected to the dashboard before this screen ever renders, so
 * the body here may assume `me.role == "admin"`. The server enforces the same
 * gate independently.
 *
 * The thresholds (auto-approve / hold) route through the Comptroller's
 * dedicated thresholds endpoint; everything else uses `config/set`. The
 * ViewModel hides the difference behind a single `save(key)` call.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    @Suppress("UNUSED_PARAMETER") navController: NavController,
    viewModel: ConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ConfigEvent.Toast -> snackbarHostState.showSnackbar(context.getString(event.messageRes))
                is ConfigEvent.Error -> {
                    val fallback = context.getString(R.string.staff_config_toast_save_failed)
                    val msg = event.error.userMessage().ifBlank { fallback }
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading && state.server.isEmpty() -> Box(modifier = Modifier.fillMaxSize()) {
                LoadingState()
            }
            else -> ConfigBody(
                state = state,
                onLocalChange = viewModel::setLocal,
                onSave = viewModel::save,
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ConfigBody(
    state: ConfigUiState,
    onLocalChange: (String, String) -> Unit,
    onSave: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Moderation
        ConfigCard(title = stringResource(R.string.staff_config_group_moderation)) {
            NumberField(
                label = stringResource(R.string.staff_config_field_auto_approve),
                suffix = stringResource(R.string.staff_config_suffix_per100),
                key = ConfigViewModel.KEY_LOW,
                state = state,
                onLocalChange = onLocalChange,
                onSave = onSave,
            )
            NumberField(
                label = stringResource(R.string.staff_config_field_hold),
                suffix = stringResource(R.string.staff_config_suffix_per100),
                key = ConfigViewModel.KEY_HIGH,
                state = state,
                onLocalChange = onLocalChange,
                onSave = onSave,
            )
        }

        // Payments
        ConfigCard(title = stringResource(R.string.staff_config_group_payments)) {
            NumberField(
                label = stringResource(R.string.staff_config_field_platform_fee),
                suffix = stringResource(R.string.staff_config_suffix_percent),
                key = ConfigViewModel.KEY_FEE,
                state = state,
                onLocalChange = onLocalChange,
                onSave = onSave,
            )
        }

        // Reviews
        ConfigCard(title = stringResource(R.string.staff_config_group_reviews)) {
            NumberField(
                label = stringResource(R.string.staff_config_field_review_window),
                suffix = stringResource(R.string.staff_config_suffix_days),
                key = ConfigViewModel.KEY_REVIEW_WINDOW,
                state = state,
                onLocalChange = onLocalChange,
                onSave = onSave,
            )
        }

        // Stripe
        ConfigCard(title = stringResource(R.string.staff_config_group_stripe)) {
            TextField(
                label = stringResource(R.string.staff_config_field_stripe_publishable),
                key = ConfigViewModel.KEY_STRIPE_PUBLISHABLE,
                state = state,
                onLocalChange = onLocalChange,
                onSave = onSave,
            )
            SecretField(
                label = stringResource(R.string.staff_config_field_stripe_secret),
                key = ConfigViewModel.KEY_STRIPE_SECRET,
                state = state,
                onLocalChange = onLocalChange,
                onSave = onSave,
            )
            SecretField(
                label = stringResource(R.string.staff_config_field_stripe_webhook),
                key = ConfigViewModel.KEY_STRIPE_WEBHOOK,
                state = state,
                onLocalChange = onLocalChange,
                onSave = onSave,
            )
            TextField(
                label = stringResource(R.string.staff_config_field_stripe_oauth_client),
                key = ConfigViewModel.KEY_STRIPE_OAUTH,
                state = state,
                onLocalChange = onLocalChange,
                onSave = onSave,
            )
        }
    }
}

@Composable
private fun ConfigCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    suffix: String,
    key: String,
    state: ConfigUiState,
    onLocalChange: (String, String) -> Unit,
    onSave: (String) -> Unit,
) {
    FieldRow(
        label = label,
        key = key,
        state = state,
        onSave = onSave,
        suffix = suffix,
    ) {
        OutlinedTextField(
            value = state.local[key].orEmpty(),
            onValueChange = { v -> onLocalChange(key, v.filter { it.isDigit() || it == '-' || it == '.' }) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TextField(
    label: String,
    key: String,
    state: ConfigUiState,
    onLocalChange: (String, String) -> Unit,
    onSave: (String) -> Unit,
) {
    FieldRow(
        label = label,
        key = key,
        state = state,
        onSave = onSave,
    ) {
        OutlinedTextField(
            value = state.local[key].orEmpty(),
            onValueChange = { v -> onLocalChange(key, v) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SecretField(
    label: String,
    key: String,
    state: ConfigUiState,
    onLocalChange: (String, String) -> Unit,
    onSave: (String) -> Unit,
) {
    FieldRow(
        label = label,
        key = key,
        state = state,
        onSave = onSave,
    ) {
        OutlinedTextField(
            value = state.local[key].orEmpty(),
            onValueChange = { v -> onLocalChange(key, v) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FieldRow(
    label: String,
    key: String,
    state: ConfigUiState,
    onSave: (String) -> Unit,
    suffix: String? = null,
    fieldContent: @Composable () -> Unit,
) {
    val server = state.server[key].orEmpty()
    val local = state.local[key].orEmpty()
    val changed = local != server
    val isSaving = state.saving[key] == true

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                fieldContent()
            }
            if (suffix != null) {
                Text(
                    text = suffix,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        if (changed) {
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { onSave(key) }, enabled = !isSaving) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    }
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(
                        if (isSaving) {
                            stringResource(R.string.staff_config_saving)
                        } else {
                            stringResource(R.string.staff_config_save)
                        },
                    )
                }
            }
        }
    }
}
