package org.mochios.android.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.mochios.android.R
import org.mochios.android.api.userMessage

@Composable
fun LoginScreen(
    uiState: AuthUiState,
    onUpdateEmail: (String) -> Unit,
    onBeginLogin: () -> Unit,
    onRequestCode: () -> Unit,
    onUpdateCode: (String) -> Unit,
    onVerifyCode: () -> Unit,
    onUpdateTotpCode: (String) -> Unit,
    onVerifyTotp: () -> Unit,
    onBeginPasskey: () -> Unit,
    onToggleRecovery: () -> Unit,
    onUpdateRecoveryCode: (String) -> Unit,
    onVerifyRecovery: () -> Unit,
    oauthScheme: String? = null,
    onStartOAuth: (String, String) -> Unit = { _, _ -> }
) {
    val oauthProviders = uiState.methods?.oauth.orEmpty()
        .filterValues { it }
        .keys
        .toList()
        .sorted()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = stringResource(R.string.auth_sign_in),
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (uiState.beginResult == null) {
            EmailEntry(
                email = uiState.email,
                isLoading = uiState.isLoading,
                onUpdateEmail = onUpdateEmail,
                onContinue = onBeginLogin
            )
            if (oauthProviders.isNotEmpty() && oauthScheme != null) {
                Spacer(modifier = Modifier.height(16.dp))
                DividerWithText(stringResource(R.string.common_or))
                Spacer(modifier = Modifier.height(16.dp))
                oauthProviders.forEach { provider ->
                    OutlinedButton(
                        onClick = { onStartOAuth(provider, oauthScheme) },
                        enabled = !uiState.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(providerLabel(provider))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        } else if (uiState.mfaPartial != null) {
            MfaSection(
                uiState = uiState,
                onUpdateEmailCode = { /* handled through parent */ },
                onUpdateTotpCode = { /* handled through parent */ },
                onComplete = { /* handled through parent */ }
            )
        } else {
            val methods = uiState.beginResult.methods
            val hasEmail = methods.contains("email")
            val hasTotp = methods.contains("totp")
            val hasPasskey = uiState.beginResult.hasPasskey

            if (hasEmail) {
                EmailCodeSection(
                    uiState = uiState,
                    onRequestCode = onRequestCode,
                    onUpdateCode = onUpdateCode,
                    onVerifyCode = onVerifyCode
                )
            }

            if (hasTotp) {
                if (hasEmail) {
                    Spacer(modifier = Modifier.height(16.dp))
                    DividerWithText(stringResource(R.string.common_or))
                    Spacer(modifier = Modifier.height(16.dp))
                }
                TotpSection(
                    totpCode = uiState.totpCode,
                    isLoading = uiState.isLoading,
                    onUpdateCode = onUpdateTotpCode,
                    onVerify = onVerifyTotp
                )
            }

            if (hasPasskey) {
                if (hasEmail || hasTotp) {
                    Spacer(modifier = Modifier.height(16.dp))
                    DividerWithText(stringResource(R.string.common_or))
                    Spacer(modifier = Modifier.height(16.dp))
                }
                OutlinedButton(
                    onClick = onBeginPasskey,
                    enabled = !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.auth_sign_in_with_passkey))
                }
            }
        }

        if (uiState.error != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = uiState.error.userMessage(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (uiState.beginResult != null) {
            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = onToggleRecovery) {
                Text(
                    if (uiState.showRecovery) stringResource(R.string.auth_back_to_sign_in)
                    else stringResource(R.string.auth_use_recovery_code)
                )
            }

            if (uiState.showRecovery) {
                RecoverySection(
                    recoveryCode = uiState.recoveryCode,
                    email = uiState.email,
                    isLoading = uiState.isLoading,
                    onUpdateCode = onUpdateRecoveryCode,
                    onVerify = onVerifyRecovery
                )
            }
        }
    }
}

@Composable
private fun EmailEntry(
    email: String,
    isLoading: Boolean,
    onUpdateEmail: (String) -> Unit,
    onContinue: () -> Unit
) {
    OutlinedTextField(
        value = email,
        onValueChange = onUpdateEmail,
        label = { Text(stringResource(R.string.auth_email)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Go
        ),
        keyboardActions = KeyboardActions(onGo = { onContinue() }),
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = onContinue,
        enabled = !isLoading && email.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.height(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(stringResource(R.string.auth_continue))
        }
    }
}

@Composable
private fun EmailCodeSection(
    uiState: AuthUiState,
    onRequestCode: () -> Unit,
    onUpdateCode: (String) -> Unit,
    onVerifyCode: () -> Unit
) {
    if (!uiState.codeSent) {
        Text(
            text = stringResource(R.string.auth_sign_in_with_email_code),
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onRequestCode,
            enabled = !uiState.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(stringResource(R.string.auth_send_code))
            }
        }
    } else {
        Text(
            text = stringResource(R.string.auth_check_email),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.code,
            onValueChange = onUpdateCode,
            label = { Text(stringResource(R.string.auth_verification_code)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(onGo = { onVerifyCode() }),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onVerifyCode,
            enabled = !uiState.isLoading && uiState.code.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(stringResource(R.string.auth_verify))
            }
        }
    }
}

@Composable
private fun TotpSection(
    totpCode: String,
    isLoading: Boolean,
    onUpdateCode: (String) -> Unit,
    onVerify: () -> Unit
) {
    Text(
        text = stringResource(R.string.auth_authenticator_app),
        style = MaterialTheme.typography.titleSmall
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = totpCode,
        onValueChange = onUpdateCode,
        label = { Text(stringResource(R.string.auth_six_digit_code)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Go
        ),
        keyboardActions = KeyboardActions(onGo = { onVerify() }),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(12.dp))
    Button(
        onClick = onVerify,
        enabled = !isLoading && totpCode.length == 6,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.height(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(stringResource(R.string.auth_verify))
        }
    }
}

@Composable
private fun MfaSection(
    uiState: AuthUiState,
    onUpdateEmailCode: (String) -> Unit,
    onUpdateTotpCode: (String) -> Unit,
    onComplete: () -> Unit
) {
    Text(
        text = stringResource(R.string.auth_mfa_title),
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.auth_mfa_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(16.dp))

    val remaining = uiState.mfaRemaining
    if (remaining.contains("email")) {
        OutlinedTextField(
            value = uiState.mfaEmailCode,
            onValueChange = onUpdateEmailCode,
            label = { Text(stringResource(R.string.auth_email_code)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
    if (remaining.contains("totp")) {
        OutlinedTextField(
            value = uiState.mfaTotpCode,
            onValueChange = onUpdateTotpCode,
            label = { Text(stringResource(R.string.auth_authenticator_code)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    Button(
        onClick = onComplete,
        enabled = !uiState.isLoading,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.height(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(stringResource(R.string.auth_continue))
        }
    }
}

@Composable
private fun RecoverySection(
    recoveryCode: String,
    email: String,
    isLoading: Boolean,
    onUpdateCode: (String) -> Unit,
    onVerify: () -> Unit
) {
    OutlinedTextField(
        value = recoveryCode,
        onValueChange = onUpdateCode,
        label = { Text(stringResource(R.string.auth_recovery_code)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onVerify() }),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(12.dp))
    Button(
        onClick = onVerify,
        enabled = !isLoading && recoveryCode.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.height(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(stringResource(R.string.auth_recover_account))
        }
    }
}

private fun providerLabel(name: String): String = when (name.lowercase()) {
    "github" -> "GitHub"
    "x" -> "X"
    else -> name.replaceFirstChar { it.uppercase() }
}

@Composable
private fun DividerWithText(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}
