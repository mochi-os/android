// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

// Fingerprints travel unhyphenated (9 chars) and display hyphenated,
// matching entity fingerprints.
fun hyphenateFingerprint(fingerprint: String): String =
    if (fingerprint.length == 9) {
        "${fingerprint.substring(0, 3)}-${fingerprint.substring(3, 6)}-${fingerprint.substring(6)}"
    } else {
        fingerprint
    }

// A peer's announced display name — a self-asserted label shown plain. The
// fingerprint (shown alongside) is the authoritative identifier; nothing
// keys logic off the name.
@Composable
fun PeerName(name: String) {
    if (name.isBlank()) return
    Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
}
