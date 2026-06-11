package org.mochios.android.ui.auth

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
                onVerifyRecovery = viewModel::verifyRecoveryCode,
                onBack = viewModel::backToEmailEntry,
                oauthScheme = oauthScheme,
                onStartOAuth = viewModel::startOAuth
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
