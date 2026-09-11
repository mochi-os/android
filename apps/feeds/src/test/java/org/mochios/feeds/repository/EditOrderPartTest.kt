// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.repository

import okhttp3.MultipartBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * One order part per item sent nothing at all once every attachment was
 * removed, so the server could not tell "remove them all" from "leave them
 * alone" and kept them. The client talks to feeds servers of any version, so
 * a non-empty order keeps the one form every server reads.
 */
class EditOrderPartTest {

    private fun sent(order: List<String>?): List<String> {
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("post", "p")
        addEditOrder(builder, order)
        return builder.build().parts
            .filter { it.headers?.get("Content-Disposition")?.contains("name=\"order\"") == true }
            .map { part -> Buffer().also { part.body.writeTo(it) }.readUtf8() }
    }

    @Test
    fun `an order is one part per item, which servers of every version read`() {
        assertEquals(listOf("a1", "new:0"), sent(listOf("a1", "new:0")))
    }

    @Test
    fun `an empty order still reaches the server`() {
        assertEquals(listOf("[]"), sent(emptyList()))
    }

    @Test
    fun `no order leaves the attachments untouched`() {
        assertEquals(emptyList<String>(), sent(null))
    }
}
