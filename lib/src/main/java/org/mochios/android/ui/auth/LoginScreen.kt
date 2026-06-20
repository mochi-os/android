// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.mochios.android.R
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.CodeInputBoxes

/**
 * Email-entry step of the sign-in flow (the "begin" screen).
 *
 * Driven by the global [AuthUiState.methods]
 * ([org.mochios.android.auth.MethodsResponse]): the email field, the passkey
 * button and one button per enabled OAuth provider. Submitting the email runs
 * the begin API; once a [org.mochios.android.auth.BeginResult] arrives the
 * navigation host advances to [AccountMethodsScreen].
 */
@Composable
fun EmailEntryScreen(
    uiState: AuthUiState,
    onUpdateEmail: (String) -> Unit,
    onBeginLogin: () -> Unit,
    onBeginPasskey: () -> Unit,
    oauthScheme: String? = null,
    onStartOAuth: (String, String) -> Unit = { _, _ -> }
) {
    AuthScreenColumn {
        Text(
            text = stringResource(R.string.auth_login_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(32.dp))
        InitialMethods(
            uiState = uiState,
            onUpdateEmail = onUpdateEmail,
            onBeginLogin = onBeginLogin,
            onBeginPasskey = onBeginPasskey,
            oauthScheme = oauthScheme,
            onStartOAuth = onStartOAuth
        )
        if (uiState.error != null) {
            Spacer(modifier = Modifier.height(12.dp))
            AuthErrorBanner(error = uiState.error)
        }
    }
}

/**
 * Per-account method step (the "methods" screen).
 *
 * The account's [org.mochios.android.auth.BeginResult.allowed] list drives the
 * email-code / authenticator / passkey options, with recovery gated on the
 * global `recovery` flag and previously-used OAuth providers shown when
 * `begin.oauth` is true. The MFA continuation (`mfaPartial != null`) replaces
 * the method list with its own section.
 */
@Composable
fun AccountMethodsScreen(
    uiState: AuthUiState,
    onRequestCode: () -> Unit,
    onUpdateCode: (String) -> Unit,
    onVerifyCode: () -> Unit,
    onUpdateTotpCode: (String) -> Unit,
    onVerifyTotp: () -> Unit,
    onBeginPasskey: () -> Unit,
    onShowRecovery: () -> Unit,
    onBack: () -> Unit,
    oauthScheme: String? = null,
    onStartOAuth: (String, String) -> Unit = { _, _ -> }
) {
    AuthScreenColumn(onBack = onBack, title = uiState.email) {
        if (uiState.mfaPartial != null) {
            Text(
                text = stringResource(R.string.auth_sign_in),
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(32.dp))
            MfaSection(uiState = uiState)
        } else {
            AccountMethods(
                uiState = uiState,
                onRequestCode = onRequestCode,
                onUpdateCode = onUpdateCode,
                onVerifyCode = onVerifyCode,
                onUpdateTotpCode = onUpdateTotpCode,
                onVerifyTotp = onVerifyTotp,
                onBeginPasskey = onBeginPasskey,
                onShowRecovery = onShowRecovery,
                oauthScheme = oauthScheme,
                onStartOAuth = onStartOAuth
            )
        }

        if (uiState.error != null) {
            Spacer(modifier = Modifier.height(12.dp))
            AuthErrorBanner(error = uiState.error)
        }
    }
}

/**
 * Recovery-code step: a dedicated screen reached from [AccountMethodsScreen]
 * when the account allows recovery. Verifying a code routes through the same
 * auth result as the other factors.
 */
@Composable
fun RecoveryScreen(
    uiState: AuthUiState,
    onUpdateCode: (String) -> Unit,
    onVerify: () -> Unit,
    onBack: () -> Unit
) {
    AuthScreenColumn(onBack = onBack, title = uiState.email) {
        RecoverySection(
            recoveryCode = uiState.recoveryCode,
            isLoading = uiState.isLoading,
            onUpdateCode = onUpdateCode,
            onVerify = onVerify
        )
        if (uiState.error != null) {
            Spacer(modifier = Modifier.height(12.dp))
            AuthErrorBanner(error = uiState.error)
        }
    }
}

/**
 * Shared vertical-scroll, centred column chrome for the auth steps. A top app
 * bar is shown when either a [title] or an [onBack] affordance is supplied,
 * carrying the account email and the back navigation; otherwise the screen has
 * no app bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthScreenColumn(
    onBack: (() -> Unit)? = null,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        topBar = {
            if (onBack != null || title != null) {
                TopAppBar(
                    title = {
                        if (title != null) {
                            Text(title)
                        }
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.auth_back)
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            content()
        }
    }
}

@Composable
private fun InitialMethods(
    uiState: AuthUiState,
    onUpdateEmail: (String) -> Unit,
    onBeginLogin: () -> Unit,
    onBeginPasskey: () -> Unit,
    oauthScheme: String?,
    onStartOAuth: (String, String) -> Unit
) {
    val methods = uiState.methods
    val showEmail = methods?.email ?: true
    val showPasskey = methods?.passkey ?: false
    val oauthProviders = methods?.oauth.orEmpty()
        .filterValues { enabled -> enabled }
        .keys
        .toList()
        .sorted()

    if (showEmail) {
        OutlinedTextField(
            value = uiState.email,
            onValueChange = onUpdateEmail,
            label = { Text(stringResource(R.string.auth_email)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(onGo = { onBeginLogin() }),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        PrimaryButton(
            text = stringResource(R.string.auth_next),
            isLoading = uiState.isLoading,
            enabled = uiState.email.isNotBlank(),
            onClick = onBeginLogin
        )
    }

    val hasAlternatives = showPasskey || (oauthProviders.isNotEmpty() && oauthScheme != null)
    if (hasAlternatives) {
        if (showEmail) {
            Spacer(modifier = Modifier.height(16.dp))
            DividerWithText(stringResource(R.string.auth_or_log_in_with))
            Spacer(modifier = Modifier.height(16.dp))
        }
        if (showPasskey) {
            MethodButton(
                text = stringResource(R.string.auth_passkey),
                iconRes = R.drawable.ic_passkey,
                enabled = !uiState.isLoading,
                onClick = onBeginPasskey
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (oauthScheme != null) {
            oauthProviders.forEach { provider ->
                MethodButton(
                    text = providerLabel(provider),
                    iconRes = providerIcon(provider),
                    iconVector = if (providerIcon(provider) == null) {
                        Icons.Default.AccountCircle
                    } else {
                        null
                    },
                    enabled = !uiState.isLoading,
                    onClick = { onStartOAuth(provider, oauthScheme) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AccountMethods(
    uiState: AuthUiState,
    onRequestCode: () -> Unit,
    onUpdateCode: (String) -> Unit,
    onVerifyCode: () -> Unit,
    onUpdateTotpCode: (String) -> Unit,
    onVerifyTotp: () -> Unit,
    onBeginPasskey: () -> Unit,
    onShowRecovery: () -> Unit,
    oauthScheme: String?,
    onStartOAuth: (String, String) -> Unit
) {
    val begin = uiState.beginResult ?: return
    val hasEmail = begin.allowed.contains("email")
    val hasTotp = begin.allowed.contains("totp")
    val showPasskey = begin.hasPasskey && begin.allowed.contains("passkey")
    val hasRecovery = uiState.methods?.recovery ?: false
    // Previously-configured OAuth providers, taken from the global methods
    // config, are offered for this account only when begin reports oauth = true.
    val oauthProviders = if (begin.oauth) {
        uiState.methods?.oauth.orEmpty()
            .filterValues { enabled -> enabled }
            .keys
            .toList()
            .sorted()
    } else {
        emptyList()
    }
    // The single Log-in button verifies whichever factor the user engaged: the
    // email code once it's been requested, otherwise the authenticator code.
    val emailActive = uiState.codeSent

    if (hasEmail) {
        if (!uiState.codeSent) {
            MethodButton(
                text = stringResource(R.string.auth_email_me_a_code),
                iconVector = Icons.Default.MailOutline,
                enabled = !uiState.isLoading,
                onClick = onRequestCode
            )
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
        }
    }

    if (hasTotp) {
        if (hasEmail) {
            Spacer(modifier = Modifier.height(16.dp))
            DividerWithText(stringResource(R.string.auth_or_enter_authenticator_code))
            Spacer(modifier = Modifier.height(16.dp))
        }
        CodeInputBoxes(
            value = uiState.totpCode,
            onValueChange = onUpdateTotpCode,
            enabled = !uiState.isLoading,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (hasEmail || hasTotp) {
        Spacer(modifier = Modifier.height(16.dp))
        PrimaryButton(
            text = stringResource(R.string.auth_log_in),
            isLoading = uiState.isLoading,
            enabled = if (emailActive) {
                uiState.code.isNotBlank()
            } else {
                uiState.totpCode.length == 6
            },
            onClick = { if (emailActive) onVerifyCode() else onVerifyTotp() }
        )
    }

    // Passkey and previously-used OAuth share a single "Or log in with" header
    // when both are offered, rather than each printing its own divider.
    val showOauth = oauthProviders.isNotEmpty() && oauthScheme != null
    if (showPasskey || showOauth) {
        Spacer(modifier = Modifier.height(16.dp))
        DividerWithText(stringResource(R.string.auth_or_log_in_with))
        Spacer(modifier = Modifier.height(16.dp))
        if (showPasskey) {
            MethodButton(
                text = stringResource(R.string.auth_use_passkey),
                iconRes = R.drawable.ic_passkey,
                enabled = !uiState.isLoading,
                onClick = onBeginPasskey
            )
            if (showOauth) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        if (oauthScheme != null) {
            oauthProviders.forEach { provider ->
                MethodButton(
                    text = providerLabel(provider),
                    iconRes = providerIcon(provider),
                    iconVector = if (providerIcon(provider) == null) {
                        Icons.Default.AccountCircle
                    } else {
                        null
                    },
                    enabled = !uiState.isLoading,
                    onClick = { onStartOAuth(provider, oauthScheme) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (hasRecovery) {
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onShowRecovery) {
            Text(stringResource(R.string.auth_lost_access_recovery))
        }
    }
}

@Composable
private fun MfaSection(uiState: AuthUiState) {
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
            onValueChange = { /* handled through parent */ },
            label = { Text(stringResource(R.string.auth_email_code)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
    if (remaining.contains("totp")) {
        OutlinedTextField(
            value = uiState.mfaTotpCode,
            onValueChange = { /* handled through parent */ },
            label = { Text(stringResource(R.string.auth_authenticator_code)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun RecoverySection(
    recoveryCode: String,
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
    PrimaryButton(
        text = stringResource(R.string.auth_recover_account),
        isLoading = isLoading,
        enabled = recoveryCode.isNotBlank(),
        onClick = onVerify
    )
}

@Composable
private fun PrimaryButton(
    text: String,
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    trailingIcon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = !isLoading && enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.height(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(text)
            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Outlined alternative-method button with a leading icon, matching the
 * "OR LOG IN WITH" rows. Supply exactly one of [iconRes] (vector drawable) or
 * [iconVector] (Material icon).
 */
@Composable
private fun MethodButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    iconRes: Int? = null,
    iconVector: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                iconRes != null -> Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                iconVector != null -> Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text)
        }
    }
}

private fun providerLabel(name: String): String = when (name.lowercase()) {
    "github" -> "GitHub"
    "x" -> "X"
    else -> name.replaceFirstChar { char -> char.uppercase() }
}

private fun providerIcon(name: String): Int? = when (name.lowercase()) {
    "github" -> R.drawable.ic_provider_github
    "google" -> R.drawable.ic_provider_google
    "microsoft" -> R.drawable.ic_provider_microsoft
    "facebook" -> R.drawable.ic_provider_facebook
    "x" -> R.drawable.ic_provider_x
    else -> null
}

/**
 * Banner-style error display for auth failures. Detects specific server
 * error codes (`suspended`, `signup_disabled`) and surfaces a heading +
 * detail message so users get a clearer story than the bare label. Other
 * errors fall back to a short error-coloured line.
 */
@Composable
private fun AuthErrorBanner(error: org.mochios.android.api.MochiError) {
    val code = when (error) {
        is org.mochios.android.api.MochiError.ForbiddenError -> error.errorCode
        is org.mochios.android.api.MochiError.AuthError -> error.errorCode
        else -> null
    }
    val headingRes = when (code) {
        "suspended" -> R.string.auth_error_suspended_title
        "signup_disabled" -> R.string.auth_error_signup_disabled_title
        else -> null
    }
    val bodyRes = when (code) {
        "suspended" -> R.string.auth_error_suspended_body
        "signup_disabled" -> R.string.auth_error_signup_disabled_body
        else -> null
    }
    if (headingRes != null && bodyRes != null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(12.dp),
        ) {
            Column {
                Text(
                    text = stringResource(headingRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = error.userMessage().ifBlank { stringResource(bodyRes) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    } else {
        Text(
            text = error.userMessage(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
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
