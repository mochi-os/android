package org.mochios.market.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.market.model.AuditEvent

/**
 * Vertical timeline of audit events for an order, listing, or dispute.
 *
 * Per entry: dot + action key + actor name + relative timestamp. The
 * action key is rendered as-is (server-emitted `order.shipped`,
 * `dispute.opened`, …) — translating those to friendly labels happens at
 * the call site via the existing per-domain map (web's
 * `components/shared/audit-labels.ts`). Renders nothing when [events] is
 * empty so a parent screen can drop the surrounding heading.
 */
@Composable
fun AuditTimeline(
    events: List<AuditEvent>,
    modifier: Modifier = Modifier,
    actionLabel: (String) -> String = { it },
) {
    if (events.isEmpty()) return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        events.forEach { event ->
            // Precomputed in composable scope — formatTimestamp is @Composable
            // and can't be called inside the buildString lambda below.
            val timestampText = if (event.timestamp > 0L) {
                LocalFormat.current.formatTimestamp(event.timestamp)
            } else {
                ""
            }
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(end = 12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(modifier = Modifier.padding(top = 2.dp))
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = actionLabel(event.action),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val sub = buildString {
                        if (event.actorName.isNotBlank()) append(event.actorName)
                        if (timestampText.isNotEmpty()) {
                            if (isNotEmpty()) append(" · ")
                            append(timestampText)
                        }
                    }
                    if (sub.isNotEmpty()) {
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
