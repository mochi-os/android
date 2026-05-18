package org.mochios.wikis.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.wikis.R
import org.mochios.wikis.ui.redirects.RedirectsBody
import org.mochios.wikis.ui.redirects.RedirectsViewModel

/**
 * Redirects tab body. Reuses the [RedirectsBody] from the standalone
 * redirects screen so both surfaces stay in sync — only the surrounding
 * chrome (TopAppBar vs settings tab strip) differs.
 *
 * The Add affordance is a FloatingActionButton in tab mode, matching the
 * other tabs (Access). The standalone screen still uses a TopAppBar
 * action because it owns its own top bar.
 *
 * Snackbars from the underlying [RedirectsViewModel] are relayed through
 * the parent's snackbar channel so the host screen's SnackbarHost displays
 * them — keeping all tab snackbars on a single host avoids them stacking
 * on each tab's local Scaffold.
 */
@Composable
fun RedirectsTab(
    parentViewModel: WikiSettingsViewModel,
    @Suppress("UNUSED_PARAMETER") tabViewModel: RedirectsTabViewModel = hiltViewModel(),
    redirectsViewModel: RedirectsViewModel = hiltViewModel(),
) {
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        redirectsViewModel.snackbar.collect { msg ->
            parentViewModel.emit(msg.messageRes, *msg.args.toTypedArray())
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.wikis_redirects_add),
                )
            }
        },
    ) { innerPadding ->
        RedirectsBody(
            viewModel = redirectsViewModel,
            showAddDialog = showAddDialog,
            onShowAddDialogChange = { showAddDialog = it },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}
