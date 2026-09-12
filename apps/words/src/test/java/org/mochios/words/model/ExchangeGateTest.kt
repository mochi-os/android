// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `action_exchange` refuses once the bag holds fewer than seven tiles. The
 * control used to be offered anyway, so late in a game the user chose tiles,
 * confirmed, and only then met the server's refusal.
 */
class ExchangeGateTest {

    private fun game(bag: Int) = Game(id = "g1", bag_count = bag)

    @Test
    fun `a full bag allows a swap`() {
        assertTrue(canExchange(game(86)))
    }

    @Test
    fun `exactly seven tiles is the server's own boundary`() {
        assertTrue(canExchange(game(EXCHANGE_MINIMUM)))
    }

    @Test
    fun `six tiles is refused, as the server refuses it`() {
        assertFalse(canExchange(game(EXCHANGE_MINIMUM - 1)))
    }

    @Test
    fun `an empty bag is refused`() {
        assertFalse(canExchange(game(0)))
    }
}
