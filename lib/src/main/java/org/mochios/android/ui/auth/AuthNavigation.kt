// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AuthNavigation(
    onAuthenticated: () -> Unit,
    oauthScheme: String? = null
) {
    val navController = rememberNavController()
    val viewModel: AuthViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // The AuthViewModel is activity-scoped and survives logout, so it still
    // holds the previous user's email and BeginResult. Wipe that on (re)entry
    // so the flow always restarts at the email step.
    LaunchedEffect(Unit) {
        viewModel.resetForLogin()
    }

    LaunchedEffect(uiState.oauthLaunchUrl) {
        val url = uiState.oauthLaunchUrl ?: return@LaunchedEffect
        val intent = androidx.browser.customtabs.CustomTabsIntent.Builder().build()
        intent.launchUrl(context, android.net.Uri.parse(url))
        viewModel.consumeOAuthLaunchUrl()
    }

    LaunchedEffect(uiState.authComplete) {
        if (uiState.authComplete) {
            onAuthenticated()
            viewModel.consumeAuthComplete()
        }
    }

    LaunchedEffect(uiState.serverValidated) {
        if (uiState.serverValidated) {
            navController.navigate("login") {
                popUpTo("serverSetup") { inclusive = true }
            }
        }
    }

    // Advance to the per-account methods step once begin returns. The block
    // reads the *current* state (not the composition snapshot) so the on-entry
    // resetForLogin — which runs first — has already cleared any stale
    // beginResult, preventing a spurious double-navigation into "account".
    LaunchedEffect(uiState.beginResult != null) {
        if (viewModel.uiState.value.beginResult != null &&
            navController.currentDestination?.route == "login") {
            navController.navigate("account")
        }
    }

    LaunchedEffect(uiState.needsIdentity) {
        if (uiState.needsIdentity) {
            navController.navigate("identity") {
                popUpTo("login") { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = "serverSetup") {
        composable("serverSetup") {
            ServerSetupScreen(
                uiState = uiState,
                onUpdateUrl = viewModel::updateServerUrl,
                onConnect = viewModel::validateServer
            )
        }

        composable("login") {
            EmailEntryScreen(
                uiState = uiState,
                onUpdateEmail = viewModel::updateEmail,
                onBeginLogin = viewModel::beginLogin,
                onBeginPasskey = viewModel::beginPasskeyAuth,
                oauthScheme = oauthScheme,
                onStartOAuth = viewModel::startOAuth
            )
        }

        composable("account") {
            // Defend against landing here without a BeginResult — e.g. the saved
            // nav back stack is restored after process death while the recreated
            // ViewModel state is empty. There's nothing to render, so drop back
            // to the email step instead of showing a blank screen.
            LaunchedEffect(Unit) {
                if (viewModel.uiState.value.beginResult == null) {
                    navController.navigate("login") {
                        popUpTo("account") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            // Clear the begin result when leaving so returning to the email step
            // (via the button or system back) doesn't immediately re-navigate.
            val leaveAccount = {
                navController.popBackStack()
                viewModel.backToEmailEntry()
            }
            BackHandler(onBack = leaveAccount)
            AccountMethodsScreen(
                uiState = uiState,
                onRequestCode = viewModel::requestEmailCode,
                onUpdateCode = viewModel::updateCode,
                onVerifyCode = viewModel::verifyCode,
                onUpdateTotpCode = viewModel::updateTotpCode,
                onVerifyTotp = viewModel::verifyTotp,
                onBeginPasskey = viewModel::beginPasskeyAuth,
                onShowRecovery = { navController.navigate("recovery") },
                onBack = leaveAccount,
                oauthScheme = oauthScheme,
                onStartOAuth = viewModel::startOAuth
            )
        }

        composable("recovery") {
            RecoveryScreen(
                uiState = uiState,
                onUpdateCode = viewModel::updateRecoveryCode,
                onVerify = viewModel::verifyRecoveryCode,
                onBack = {
                    navController.popBackStack()
                    viewModel.clearRecovery()
                }
            )
        }

        composable("identity") {
            IdentityScreen(
                uiState = uiState,
                onUpdateName = viewModel::updateIdentityName,
                onUpdatePrivacy = viewModel::updateIdentityPrivacy,
                onCreate = viewModel::createIdentity,
                onAbandon = viewModel::abandonIdentity,
            )
        }
    }
}
