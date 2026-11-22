// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.components

import org.mochios.android.i18n.Format
import org.mochios.android.share.Shared
import org.mochios.market.lib.formatPrice
import org.mochios.market.model.Listing

/**
 * A listing as it is shared: its title, then a blank line and its price
 * where it has one, since a feed's post joins lines a single newline parts;
 * and its page on the user's [server], `<server>/market/listings/<id>`,
 * which says the rest.
 */
internal fun listingShare(listing: Listing, server: String, format: Format): Shared {
    val currency = listing.currency
    val price = if (listing.price > 0 && currency != null) formatPrice(listing.price, currency, format) else ""
    return Shared(
        text = if (price.isEmpty()) listing.title else listing.title + "\n\n" + price,
        url = server.trimEnd('/') + "/market/listings/" + listing.id,
    )
}
