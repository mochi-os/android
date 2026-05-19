package org.mochios.staff.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Headline metric tile used by the staff dashboard. Mirrors the `StatCard`
 * helper in `apps/staff/web/src/features/dashboard/dashboard-page.tsx`:
 *
 *  - small uppercase caption (the metric label),
 *  - large headline value (the metric, e.g. "1,234" or a formatted price),
 *  - optional sub-label (e.g. "5 currencies" or "across 4 zones").
 *
 * Surface tone is [androidx.compose.material3.ColorScheme.surfaceVariant] so
 * the tile sits one level above the page background regardless of which
 * Mochi theme is active. Used 7+ times on the dashboard — the surrounding
 * grid (`LazyVerticalGrid(GridCells.Adaptive(180.dp))`) sizes each tile.
 */
@Composable
fun KpiCard(
    label: String,
    value: String,
    subLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subLabel != null) {
                Text(
                    text = subLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
