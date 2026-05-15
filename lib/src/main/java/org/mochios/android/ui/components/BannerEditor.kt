package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.R

/**
 * Markdown banner editor shared across feeds, forums (and other entity-
 * scoped settings that surface a "banner" field).
 *
 * `initialValue` seeds the local draft state; tapping Save invokes
 * `onSave(currentDraft)`, Clear invokes `onSave("")`.
 */
@Composable
fun BannerEditor(
    initialValue: String,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.banner_label),
    description: String = stringResource(R.string.banner_description),
) {
    var draft by remember(initialValue) { mutableStateOf(initialValue) }

    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = { Text(stringResource(R.string.banner_placeholder)) },
            minLines = 4,
            maxLines = 12,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row {
            OutlinedButton(
                onClick = { onSave(draft) },
                enabled = draft != initialValue,
            ) {
                Text(stringResource(R.string.banner_save))
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (initialValue.isNotEmpty()) {
                OutlinedButton(onClick = {
                    draft = ""
                    onSave("")
                }) {
                    Text(stringResource(R.string.banner_clear))
                }
            }
        }
    }
}
