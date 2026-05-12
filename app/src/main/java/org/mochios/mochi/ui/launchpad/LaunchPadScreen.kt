package org.mochios.mochi.ui.launchpad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.chat.navigation.ChatApp
import org.mochios.feeds.navigation.FeedsApp
import org.mochios.mochi.R

private data class LaunchpadTile(
    val label: String,
    val route: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaunchPadScreen(
    onAppSelected: (String) -> Unit,
) {
    val tiles = remember {
        listOf(
            LaunchpadTile(label = "Feeds", route = FeedsApp.HOME),
            LaunchpadTile(label = "Chat", route = ChatApp.HOME),
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tiles, key = { it.route }) { tile ->
                Button(
                    onClick = { onAppSelected(tile.route) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(tile.label)
                }
            }
        }
    }
}
