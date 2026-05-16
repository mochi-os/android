package org.mochios.feeds.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.feeds.R
import org.mochios.android.R as MochiR

/**
 * Standalone Sources page reachable from the feed's overflow menu.
 * Mirrors web's `/$feedId_/sources` route. The body is the same composable
 * the Settings tab used (now removed from there).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(
    onNavigateBack: () -> Unit,
    viewModel: FeedSettingsViewModel = hiltViewModel(),
) {
    val feedInfo by viewModel.feedInfo.collectAsState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.loadSources()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        feedInfo?.name?.let { name ->
                            stringResource(R.string.feeds_sources_title_named, name)
                        } ?: stringResource(R.string.feeds_sources_title),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
            SourcesTab(viewModel = viewModel)
        }
    }
}
