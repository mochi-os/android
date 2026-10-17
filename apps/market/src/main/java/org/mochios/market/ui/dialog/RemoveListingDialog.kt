// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.mochios.android.R as MochiR
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.market.R
import org.mochios.market.model.Listing
import org.mochios.market.model.ListingStatus
import org.mochios.market.model.RemovalCheck

/**
 * Confirms deleting a draft or removing a listing from sale, as the web does:
 * the removal warning names the live bidders or subscribers that [check] says
 * removal cuts off, and is the generic one when there are none or the check
 * failed.
 *
 * @param listing the listing being deleted or removed.
 * @param check what removal cuts off, from `listings/removal/check`; null for
 *   a draft or when the check failed.
 * @param onConfirm called when the seller confirms.
 * @param onDismiss called when the seller cancels or dismisses the dialog.
 */
@Composable
fun RemoveListingDialog(
    listing: Listing,
    check: RemovalCheck?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val draft = listing.status == ListingStatus.DRAFT
    val format = LocalFormat.current
    val message = when {
        draft -> stringResource(R.string.market_listings_delete_message)
        check?.hasActiveAuction == true && check.activeBidders > 0 -> pluralStringResource(
            R.plurals.market_listings_remove_message_bidders,
            check.activeBidders.toInt(),
            format.formatNumber(check.activeBidders.toInt()),
        )
        check?.hasActiveAuction == true ->
            stringResource(R.string.market_listings_remove_message_auction)
        check != null && check.activeSubscribers > 0 -> pluralStringResource(
            R.plurals.market_listings_remove_message_subscribers,
            check.activeSubscribers.toInt(),
            format.formatNumber(check.activeSubscribers.toInt()),
        )
        else -> stringResource(R.string.market_listings_remove_message)
    }
    val title = if (draft) {
        R.string.market_listings_delete_title
    } else {
        R.string.market_listings_remove_title
    }
    val confirm = if (draft) {
        R.string.market_listings_delete_confirm
    } else {
        R.string.market_listings_remove_confirm
    }
    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(title),
        text = message,
        confirmText = stringResource(confirm),
        onConfirm = onConfirm,
        destructive = true,
        dismissText = stringResource(MochiR.string.common_cancel),
    )
}
