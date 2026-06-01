package org.mochios.android.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mochios.android.R
import org.mochios.android.auth.StepUpClient
import org.mochios.android.auth.StepUpResult

// Brand names stay verbatim in Latin script in every locale.
private val OAUTH_LABELS = mapOf(
    "google" to "Google",
    "github" to "GitHub",
    "microsoft" to "Microsoft",
    "facebook" to "Facebook",
    "x" to "X",
)

private fun oauthLabel(key: String): String =
    OAUTH_LABELS[key] ?: key.replaceFirstChar { it.uppercase() }

/**
 * Re-verifies the user with the same factor(s) they log in with (email code /
 * authenticator / passkey / linked third-party login) before a sensitive
 * account-security change, then hands the caller a single-use proof token via
 * [onVerified]. Mirrors lib/web's StepUpDialog.
 *
 * Render only while a step-up is in flight; it loads the user's factors and,
 * when email is the sole offered factor, auto-sends the code on first
 * composition. [onVerified] should clear whatever flag controls rendering.
 */
@Composable
fun StepUpDialog(
    client: StepUpClient,
    onDismiss: () -> Unit,
    onVerified: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var remaining by remember { mutableStateOf<List<String>>(emptyList()) }
    var providers by remember { mutableStateOf<List<String>>(emptyList()) }
    var sent by remember { mutableStateOf(false) }
    var emailCode by remember { mutableStateOf("") }
    var totpCode by remember { mutableStateOf("") }

    val errStart = stringResource(R.string.stepup_error_start)
    val errCode = stringResource(R.string.stepup_error_code)
    val errPasskey = stringResource(R.string.stepup_error_passkey)
    val errOauth = stringResource(R.string.stepup_error_oauth)

    // On first composition: learn the user's factors and, if email is the only
    // offered factor, send the code right away.
    LaunchedEffect(Unit) {
        try {
            val methods = client.methods()
            remaining = methods
            val linked = runCatching { client.oauthProviders() }.getOrDefault(emptyList())
            providers = linked
            if (methods.contains("email") && linked.isEmpty()) {
                runCatching { client.send() }
                sent = true
            }
        } catch (_: Exception) {
            error = errStart
        } finally {
            loading = false
        }
    }

    fun apply(result: StepUpResult) {
        val token = result.token
        if (token != null) {
            onVerified(token)
        } else {
            remaining = result.remaining ?: emptyList()
        }
    }

    fun verifyCode(run: suspend () -> StepUpResult, reset: () -> Unit) {
        scope.launch {
            busy = true
            error = null
            try {
                apply(run())
                reset()
            } catch (_: Exception) {
                error = errCode
            } finally {
                busy = false
            }
        }
    }

    fun submit() {
        if (remaining.contains("email") && emailCode.isNotBlank()) {
            verifyCode({ client.verifyEmail(emailCode.trim()) }, { emailCode = "" })
        } else if (remaining.contains("totp") && totpCode.isNotBlank()) {
            verifyCode({ client.verifyTotp(totpCode.trim()) }, { totpCode = "" })
        }
    }

    fun usePasskey() {
        scope.launch {
            busy = true
            error = null
            try {
                apply(client.passkey())
            } catch (_: Exception) {
                error = errPasskey
            } finally {
                busy = false
            }
        }
    }

    fun useOauth(provider: String) {
        scope.launch {
            busy = true
            error = null
            try {
                val url = client.oauthBegin(provider)
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                }
                // Poll for the proof the provider callback stores server-side,
                // keyed by the verifier the client holds. Mirrors the web popup
                // poll: up to two minutes, then give up.
                val deadline = System.currentTimeMillis() + 120_000
                while (System.currentTimeMillis() < deadline) {
                    delay(1000)
                    val result = client.oauthPoll()
                    if (result.token != null || result.remaining != null) {
                        apply(result)
                        break
                    }
                }
            } catch (_: Exception) {
                error = errOauth
            } finally {
                busy = false
            }
        }
    }

    val canVerify = !busy && (
        (remaining.contains("email") && emailCode.isNotBlank()) ||
            (remaining.contains("totp") && totpCode.isNotBlank())
        )
    val showFooterVerify = !loading && (remaining.contains("email") || remaining.contains("totp"))

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.stepup_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.stepup_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                } else {
                    if (remaining.contains("email")) {
                        if (sent) {
                            OutlinedTextField(
                                value = emailCode,
                                onValueChange = { emailCode = it },
                                label = { Text(stringResource(R.string.stepup_email_label)) },
                                placeholder = { Text(stringResource(R.string.stepup_email_placeholder)) },
                                singleLine = true,
                                enabled = !busy,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        error = null
                                        runCatching { client.send() }.onSuccess { sent = true }
                                    }
                                },
                                enabled = !busy,
                            ) { Text(stringResource(R.string.stepup_resend_email)) }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        error = null
                                        runCatching { client.send() }.onSuccess { sent = true }
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.stepup_send_email)) }
                        }
                    }
                    if (remaining.contains("totp")) {
                        OutlinedTextField(
                            value = totpCode,
                            onValueChange = { totpCode = it.filter { c -> c.isDigit() }.take(6) },
                            label = { Text(stringResource(R.string.stepup_totp_label)) },
                            placeholder = { Text(stringResource(R.string.stepup_totp_placeholder)) },
                            singleLine = true,
                            enabled = !busy,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (remaining.contains("passkey")) {
                        OutlinedButton(
                            onClick = { usePasskey() },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.stepup_use_passkey)) }
                    }
                    providers.forEach { provider ->
                        OutlinedButton(
                            onClick = { useOauth(provider) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.stepup_continue_with, oauthLabel(provider)))
                        }
                    }
                    error?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Default,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (showFooterVerify) {
                TextButton(onClick = { submit() }, enabled = canVerify) {
                    Text(stringResource(R.string.stepup_verify))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
