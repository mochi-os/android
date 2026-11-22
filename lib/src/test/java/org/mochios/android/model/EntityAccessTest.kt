// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Each access level allows what the web's entity-access.ts lets it do, and no more. */
class EntityAccessTest {

    private fun allowed(access: String?) = listOf(
        canDesign(access),
        canWrite(access),
        canCreate(access),
        canComment(access),
    )

    @Test
    fun `each level allows what the web's ladder gives it`() {
        // design, write, create, comment
        assertEquals(listOf(true, true, true, true), allowed("owner"))
        assertEquals(listOf(true, true, true, true), allowed("design"))
        assertEquals(listOf(false, true, true, true), allowed("write"))
        assertEquals(listOf(false, false, false, true), allowed("comment"))
        assertEquals(listOf(false, false, false, false), allowed("view"))
    }

    @Test
    fun `an unknown or missing level allows nothing`() {
        assertEquals(listOf(false, false, false, false), allowed(null))
        assertEquals(listOf(false, false, false, false), allowed(""))
        assertEquals(listOf(false, false, false, false), allowed("manage"))
        assertEquals(listOf(false, false, false, false), allowed("Owner"))
    }

    /** A view model's own no-argument canDesign() still reaches the helper. */
    private class Holder(private val access: String) {
        fun canDesign(): Boolean = canDesign(access)
    }

    @Test
    fun `a member of the same name calls through to the helper`() {
        assertEquals(true, Holder("design").canDesign())
        assertEquals(false, Holder("write").canDesign())
    }
}
