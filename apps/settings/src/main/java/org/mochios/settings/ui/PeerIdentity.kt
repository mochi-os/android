package org.mochios.settings.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import org.mochios.settings.R

// Fingerprints travel unhyphenated (9 chars) and display hyphenated,
// matching entity fingerprints.
fun hyphenateFingerprint(fingerprint: String): String =
    if (fingerprint.length == 9) {
        "${fingerprint.substring(0, 3)}-${fingerprint.substring(3, 6)}-${fingerprint.substring(6)}"
    } else {
        fingerprint
    }

// A peer's claimed display name: plain when verified, muted with an
// "(unverified)" marker when not. Names are display-only — nothing may
// key logic off them.
@Composable
fun PeerName(name: String, verified: Boolean) {
    if (name.isBlank()) return
    if (verified) {
        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    } else {
        Text(
            stringResource(R.string.peer_name_unverified, name),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
