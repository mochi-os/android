package org.mochios.projects.ui.`object`

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.projects.R
import org.mochios.projects.model.Activity
import org.mochios.projects.model.ProjectDetails

@Composable
fun ActivityTab(
    activity: List<Activity>,
    projectDetails: ProjectDetails,
    // Builds the avatar proxy URL for an activity actor. Should return an
    // absolute URL to the projects app's proxy action, e.g.
    // "<server>/projects/<project>/-/activity/<activity.id>/asset/avatar".
    avatarUrlBuilder: ((Activity) -> String?)? = null
) {
    if (activity.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.projects_activity_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(activity, key = { it.id }) { item ->
            ActivityItem(
                item = item,
                projectDetails = projectDetails,
                avatarUrl = avatarUrlBuilder?.invoke(item)
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun ActivityItem(
    item: Activity,
    projectDetails: ProjectDetails,
    avatarUrl: String?
) {
    val icon = when (item.action) {
        "created" -> Icons.Default.Add
        "deleted" -> Icons.Default.Delete
        else -> Icons.Default.Edit
    }

    val fieldName = if (item.field.isNotBlank()) {
        // Try to find field name from project details
        var name = item.field
        for ((_, fields) in projectDetails.fields) {
            val found = fields.find { it.id == item.field }
            if (found != null) {
                name = found.name
                break
            }
        }
        name
    } else ""

    // Resolve option IDs to names for enumerated fields
    fun resolveValue(value: String): String {
        if (value.isBlank()) return value
        for ((_, classOptions) in projectDetails.options) {
            for ((_, fieldOptions) in classOptions) {
                val opt = fieldOptions.find { it.id == value }
                if (opt != null) return opt.name
            }
        }
        return value
    }

    val oldDisplay = resolveValue(item.oldvalue)
    val newDisplay = resolveValue(item.newvalue)

    val description = when (item.action) {
        "created" -> stringResource(R.string.projects_activity_created)
        "deleted" -> stringResource(R.string.projects_activity_deleted)
        else -> {
            if (fieldName.isNotBlank()) {
                if (oldDisplay.isNotBlank() && newDisplay.isNotBlank()) {
                    stringResource(R.string.projects_activity_changed, fieldName, oldDisplay, newDisplay)
                } else if (newDisplay.isNotBlank()) {
                    stringResource(R.string.projects_activity_set, fieldName, newDisplay)
                } else if (oldDisplay.isNotBlank()) {
                    stringResource(R.string.projects_activity_cleared, fieldName)
                } else {
                    stringResource(R.string.projects_activity_updated, fieldName)
                }
            } else {
                stringResource(R.string.projects_activity_made_change)
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        EntityAvatar(
            name = item.name,
            src = avatarUrl,
            seed = item.user,
            size = 20.dp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = LocalFormat.current.formatTimestamp(item.created),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

