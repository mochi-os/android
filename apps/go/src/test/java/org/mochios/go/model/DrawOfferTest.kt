// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Offer draw" applies only while no offer at all is pending. The server
 * refuses an offer placed over the opponent's, and the accept/decline banner
 * already answers that one, so the menu must not offer it either.
 */
class DrawOfferTest {
    @Test
    fun offerableWhileNothingIsPending() {
        assertTrue(Game(status = "active", drawOffer = null).canOfferDraw)
        assertTrue(Game(status = "active", drawOffer = "").canOfferDraw)
    }

    @Test
    fun notOfferableOverAnyPendingOffer() {
        assertFalse(Game(status = "active", drawOffer = "1me").canOfferDraw)
        assertFalse(Game(status = "active", drawOffer = "1them").canOfferDraw)
    }

    @Test
    fun notOfferableOnceTheGameHasEnded() {
        assertFalse(Game(status = "draw", drawOffer = null).canOfferDraw)
        assertFalse(Game(status = "resigned", drawOffer = null).canOfferDraw)
    }
}
