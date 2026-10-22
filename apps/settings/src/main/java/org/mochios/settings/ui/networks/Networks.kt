// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.networks

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.mochios.android.util.NaturalCompare
import org.mochios.settings.R
import org.mochios.settings.api.Handle
import org.mochios.settings.api.NetworkAccount
import org.mochios.settings.api.NetworkFeed
import org.mochios.settings.api.NetworksData
import java.text.Normalizer

// What the Networks screen and the owner's Handles screen share: the networks'
// names, why a name cannot be reserved, and the rules the web page applies
// (apps/settings/web/src/features/user/networks.tsx).

/**
 * A network's name. The fediverse is a common noun the languages make their
 * own; XMPP is an initialism; the rest are brands, the same in every language.
 */
@Composable
internal fun networkLabel(type: String): String = when (type) {
    "fediverse" -> stringResource(R.string.networks_fediverse)
    "xmpp" -> "XMPP"
    "bluesky" -> "Bluesky"
    "matrix" -> "Matrix"
    "mastodon" -> "Mastodon"
    "lemmy" -> "Lemmy"
    else -> type
}

/** Why a name cannot be reserved, in the user's words. */
@Composable
internal fun availabilityReason(reason: String): String = stringResource(
    when (reason) {
        "invalid" -> R.string.networks_reason_invalid
        "domain" -> R.string.networks_reason_domain
        "blocked" -> R.string.networks_reason_blocked
        "protected" -> R.string.networks_reason_protected
        "taken" -> R.string.networks_reason_taken
        "parent" -> R.string.networks_reason_parent
        "limit" -> R.string.networks_reason_limit
        else -> R.string.networks_reason_unavailable
    },
)

/**
 * A handle suggested from the person's name: its letters and digits,
 * lowercased and unaccented, within the rule of three to twenty characters
 * starting with a letter.
 */
internal fun suggestHandle(name: String): String {
    // Decomposed, an accent is a mark of its own, which the letters-and-digits
    // filter drops with the rest.
    val cleaned = Normalizer.normalize(name.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("[^a-z0-9]"), "")
        .replace(Regex("^[0-9]+"), "")
        .take(20)
    return if (cleaned.length >= 3) cleaned else ""
}

/** The handles the screen shows as cards: those that sit under no other. */
internal fun parentHandles(handles: List<Handle>): List<Handle> =
    handles.filter { it.kind == "handle" && it.parent.isEmpty() }
        .sortedWith(compareBy(NaturalCompare) { it.name })

/** The names under a handle, on its domain. */
internal fun childHandles(handles: List<Handle>, parent: Handle): List<Handle> =
    handles.filter { it.kind == "handle" && it.parent == parent.name && it.domain == parent.domain }
        .sortedWith(compareBy(NaturalCompare) { it.name })

/** Whether the user may still reserve a handle: the server hosts a network and the limit allows it. */
internal fun mayReserve(data: NetworksData): Boolean =
    data.networks.isNotEmpty() && (data.handles.unlimited || data.handles.counted < data.handles.limit)

/** The hosted account a network made of a handle, if switched on. */
internal fun hostedAccount(accounts: List<NetworkAccount>, handle: Handle, network: String): NetworkAccount? =
    accounts.firstOrNull { it.hosted && it.type == network && it.identifier == handle.addresses[network] }

/**
 * The server another app is given to sign in to an account: the bare domain
 * for XMPP, an address for the rest.
 */
internal fun serverAddress(account: NetworkAccount): String {
    val domain = account.domain.ifEmpty { account.server }
    return when {
        account.type == "xmpp" -> domain
        domain.isNotEmpty() -> "https://$domain"
        else -> ""
    }
}

/** Whether an account has a home feed: a hosted fediverse or Bluesky account of a person. */
internal fun homed(account: NetworkAccount): Boolean =
    account.hosted && account.kind != "group" && (account.type == "fediverse" || account.type == "bluesky")

/** What an account's card says of it, most pressing first, as the web's status column does. */
internal sealed interface AccountStatus {
    data class Failed(val message: String) : AccountStatus
    data class Moved(val to: String) : AccountStatus
    data object HomeDeleted : AccountStatus
    data object HomePrivate : AccountStatus
    data object NoRecovery : AccountStatus
    data object Connected : AccountStatus
    data object Disconnected : AccountStatus
    data class Server(val server: String) : AccountStatus
}

internal fun accountStatus(account: NetworkAccount, feeds: List<NetworkFeed>): AccountStatus {
    val chat = account.type == "xmpp" || account.type == "matrix"
    val home = if (account.home.isNotEmpty()) feeds.firstOrNull { it.id == account.home } else null
    return when {
        account.error.isNotEmpty() -> AccountStatus.Failed(account.error)
        account.moved.isNotEmpty() -> AccountStatus.Moved(account.moved)
        account.home.isNotEmpty() && home == null -> AccountStatus.HomeDeleted
        home != null && home.privacy != "public" -> AccountStatus.HomePrivate
        account.hosted && account.type == "bluesky" && !account.recovery -> AccountStatus.NoRecovery
        !account.hosted && chat -> if (account.connected) AccountStatus.Connected else AccountStatus.Disconnected
        else -> AccountStatus.Server(account.server)
    }
}

/** The status in words, and whether it is a problem the user should see as one. */
@Composable
internal fun statusText(status: AccountStatus): Pair<String, Boolean> = when (status) {
    is AccountStatus.Failed -> status.message to true
    is AccountStatus.Moved -> stringResource(R.string.networks_moved_to, status.to) to false
    AccountStatus.HomeDeleted -> stringResource(R.string.networks_home_deleted) to true
    AccountStatus.HomePrivate -> stringResource(R.string.networks_home_private) to true
    AccountStatus.NoRecovery -> stringResource(R.string.networks_no_recovery) to true
    AccountStatus.Connected -> stringResource(R.string.networks_connected) to false
    AccountStatus.Disconnected -> stringResource(R.string.networks_not_connected) to false
    is AccountStatus.Server -> status.server to false
}
