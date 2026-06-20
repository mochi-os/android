// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.page

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mochios.wikis.R
import org.mochios.wikis.ui.components.TocHeading

/**
 * Phone-only collapsible table of contents shown above the rendered markdown.
 *
 * Mirrors the web `TableOfContents` (mobile variant) in
 * `apps/wikis/web/src/features/wiki/page-view.tsx` lines 33-100 — wrapped in a
 * `<details>` accordion with the same "On this page" header. The desktop
 * sticky-sidebar variant is intentionally absent: a phone screen never has
 * room for a 240dp right-rail next to the article body, so the collapsible is
 * the only sensible presentation.
 *
 * Headings indent by `(level - 2) * 16dp` — matching web's `ps-5` / `ps-8`
 * classes for level 3 / 4 — so the visual hierarchy survives the platform port.
 *
 * @param headings H2..H4 entries in document order (built by
 *                 [extractHeadings] inside MarkdownContent).
 * @param activeId Optional id of the currently-active heading. The row with
 *                 the matching id is rendered bold; pass `null` to leave every
 *                 row at default weight (e.g. while the active-heading tracker
 *                 hasn't run yet).
 * @param onHeadingTap Invoked with the heading id when the user taps a row.
 *                     The host typically scrolls the article to that id and
 *                     updates [activeId] in its own state.
 * @param modifier Layout modifier for the outer card.
 */
@Composable
fun TableOfContents(
    headings: List<TocHeading>,
    activeId: String?,
    onHeadingTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (headings.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "wikis_toc_chevron",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.wikis_pageview_toc),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) R.string.wikis_pageview_toc_collapse
                    else R.string.wikis_pageview_toc_expand,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotation),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider()
                Spacer(Modifier.size(4.dp))
                headings.forEach { heading ->
                    HeadingRow(
                        heading = heading,
                        isActive = heading.id == activeId,
                        onClick = { onHeadingTap(heading.id) },
                    )
                }
                Spacer(Modifier.size(4.dp))
            }
        }
    }
}

@Composable
private fun HeadingRow(
    heading: TocHeading,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    // Web uses ps-5 / ps-8 for level 3 / 4 — translate as (level - 2) * 16dp,
    // so H2 = 16dp, H3 = 32dp, H4 = 48dp from the surface edge.
    val indent = ((heading.level - 2).coerceAtLeast(0) * 16).dp
    Text(
        text = heading.text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
        color = if (isActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start = 16.dp + indent,
                end = 16.dp,
                top = 8.dp,
                bottom = 8.dp,
            ),
    )
}
