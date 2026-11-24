// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The editor refuses a digital asset over the Comptroller's 100 MB before
 * sending it: the Comptroller would refuse it only after the whole upload.
 */
class AssetLimitTest {

    // A file of the given size without its bytes.
    private fun file(name: String, size: Long) = object : File(name) {
        override fun length(): Long = size
    }

    @Test
    fun `the maximum is the Comptroller's 100 MB`() {
        assertEquals(100L * 1024 * 1024, ASSET_SIZE_MAXIMUM)
    }

    @Test
    fun `a file over the maximum is refused and the rest are sent`() {
        val film = file("film.mp4", ASSET_SIZE_MAXIMUM + 1)
        val book = file("book.pdf", ASSET_SIZE_MAXIMUM)
        val notes = file("notes.txt", 10)
        val (accepted, refused) = assetsWithinLimit(listOf(film, book, notes))
        assertEquals(listOf("book.pdf", "notes.txt"), accepted.map { it.name })
        assertEquals(listOf("film.mp4"), refused.map { it.name })
    }
}
