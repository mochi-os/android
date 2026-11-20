// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** One table names the services, for the sign-in buttons and every other screen alike. */
class ProviderNameTest {

    @Test
    fun `a listed key is named as its brand writes itself, whatever its case`() {
        assertEquals("GitHub", providerName("github"))
        assertEquals("GitHub", providerName("GitHub"))
        assertEquals("GitHub", providerName("Github"))
        assertEquals("Microsoft", providerName("microsoft"))
        assertEquals("X", providerName("x"))
    }

    @Test
    fun `a key not listed is shown capitalised`() {
        assertEquals("Gitlab", providerName("gitlab"))
    }
}
