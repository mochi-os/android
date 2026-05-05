package org.mochi.android.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AuthNavigation(onAuthenticated: () -> Unit) {
    val navController = rememberNavController()
    val viewModel: AuthViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.authComplete) {
        if (uiState.authComplete) {
            onAuthenticated()
        }
    }

    LaunchedEffect(uiState.serverValidated) {
        if (uiState.serverValidated) {
            navController.navigate("login") {
                popUpTo("serverSetup") { inclusive = true }
            }
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
            LoginScreen(
                uiState = uiState,
                onUpdateEmail = viewModel::updateEmail,
                onBeginLogin = viewModel::beginLogin,
                onRequestCode = viewModel::requestEmailCode,
                onUpdateCode = viewModel::updateCode,
                onVerifyCode = viewModel::verifyCode,
                onUpdateTotpCode = viewModel::updateTotpCode,
                onVerifyTotp = viewModel::verifyTotp,
                onBeginPasskey = viewModel::beginPasskeyAuth,
                onToggleRecovery = viewModel::toggleRecovery,
                onUpdateRecoveryCode = viewModel::updateRecoveryCode,
                onVerifyRecovery = viewModel::verifyRecoveryCode
            )
        }

        composable("identity") {
            IdentityScreen(
                uiState = uiState,
                onUpdateName = viewModel::updateIdentityName,
                onCreate = viewModel::createIdentity
            )
        }
    }
}
