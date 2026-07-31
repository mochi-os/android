// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Mochi shell registers a UnifiedPush distributor itself, and the connector
 * returns installed distributors in PackageManager order with no preference for
 * the caller. Taking the first entry therefore routed a self-hosted user's
 * notifications through ntfy or NextPush whenever one of those sorted earlier.
 */
class DistributorChoiceTest {

    private val own = "org.mochios.mochi"

    @Test
    fun `our own distributor wins wherever it appears in the list`() {
        assertEquals(own, preferOwnDistributor(listOf(own), own))
        assertEquals(own, preferOwnDistributor(listOf(own, "sh.ntfy"), own))
        // The case the old code got wrong: a third party sorted first.
        assertEquals(own, preferOwnDistributor(listOf("sh.ntfy", own), own))
        assertEquals(
            own,
            preferOwnDistributor(listOf("sh.ntfy", "org.unifiedpush.distributor.nextpush", own), own),
        )
    }

    @Test
    fun `a third party is adopted only when ours is absent`() {
        assertEquals("sh.ntfy", preferOwnDistributor(listOf("sh.ntfy"), own))
        assertEquals(
            "sh.ntfy",
            preferOwnDistributor(listOf("sh.ntfy", "org.unifiedpush.distributor.nextpush"), own),
        )
    }

    /** Not a substring or prefix match — only the exact package is ours. */
    @Test
    fun `a lookalike package is not treated as ours`() {
        assertEquals("org.mochios.mochi.evil", preferOwnDistributor(listOf("org.mochios.mochi.evil"), own))
        assertEquals("org.mochios", preferOwnDistributor(listOf("org.mochios"), own))
    }
}
