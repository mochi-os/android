// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.account

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.market.model.Account

/**
 * A profile opened by fingerprint shows the avatar of the account the
 * Comptroller answered: the fingerprint names no entity the avatar route
 * could serve.
 */
class ProfileIdentityTest {

    private val id = "a1".padEnd(50, 'x')

    @Test
    fun `a profile opened by fingerprint shows the answered account's avatar`() {
        assertEquals(id, profileIdentity(Account(id = id), "Abc234xyz"))
    }

    @Test
    fun `an answer without an id keeps the address`() {
        assertEquals(id, profileIdentity(Account(), id))
    }
}
