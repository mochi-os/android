// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.person

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.StatusBadge
import org.mochios.android.ui.components.StatusBadgeSize
import org.mochios.android.ui.components.StatusTone
import org.mochios.android.ui.components.parseHexColour
import org.mochios.people.R

/**
 * A person's banner with their avatar overlapping it, then their name,
 * fingerprint and an optional status pill, laid out edge to edge.
 *
 * @param name the name shown under the banner.
 * @param seed the identity the avatar's fallback colour is drawn from.
 * @param fingerprint the fingerprint shown under the name; hidden when blank.
 * @param avatarUrl the avatar image, or null for initials.
 * @param bannerUrl the banner image, or null for a plain band tinted with [accent].
 * @param accent the person's accent colour as hex, or null for none.
 * @param status the pill shown beside the name, or null for none.
 */
@Composable
internal fun PersonHeader(
    name: String,
    seed: String,
    fingerprint: String,
    avatarUrl: String?,
    bannerUrl: String?,
    accent: String?,
    status: PersonStatus? = null,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        if (bannerUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(bannerUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(R.string.people_person_banner_alt, name),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f)
                    .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)),
            )
        } else {
            Surface(
                color = parseHexColour(accent)?.copy(alpha = 0.12f)
                    ?: MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)),
            ) {}
        }
        EntityAvatar(
            name = name,
            src = avatarUrl,
            seed = seed,
            size = 96.dp,
            accent = accent,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 16.dp, y = 48.dp),
        )
    }

    Spacer(Modifier.height(56.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = parseHexColour(accent) ?: MaterialTheme.colorScheme.onSurface,
            )
            if (fingerprint.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = fingerprint,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        status?.let { pill ->
            StatusBadge(
                label = stringResource(pill.label),
                tone = pill.tone,
                size = StatusBadgeSize.Regular,
            )
        }
    }
}

/** Where the viewer stands with a person, as the pill [PersonHeader] shows. */
internal enum class PersonStatus(val label: Int, val tone: StatusTone) {
    FRIEND(R.string.people_person_state_friend, StatusTone.Positive),
    SELF(R.string.people_person_state_self, StatusTone.Neutral),
    INVITED(R.string.people_person_state_invited, StatusTone.Waiting),
    CONTACT(R.string.people_person_state_contact, StatusTone.Neutral),
}
