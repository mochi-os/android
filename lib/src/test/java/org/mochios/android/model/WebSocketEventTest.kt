// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * The injected Gson uses identity field naming, so a snake_case key on the wire
 * only reaches a camelCase property through @SerializedName. PushService drops
 * any event whose subId is null, so a missed annotation here silences every
 * live push rather than failing loudly.
 */
class WebSocketEventTest {
    private val gson = org.mochios.android.api.ApiClient.provideGson()

    // Verbatim from core/server/accounts.go:1722 - the local fast-path envelope.
    private val envelope = """{"sub_id":"s-7f3c","payload":"{\"tag\":\"n1\"}","account":"a-42"}"""

    @Test
    fun `the live push envelope resolves every field PushService reads`() {
        val event = gson.fromJson(envelope, WebSocketEvent::class.java)
        assertEquals("s-7f3c", event.subId)
        assertEquals("""{"tag":"n1"}""", event.payload)
        assertEquals("a-42", event.account)
    }

    @Test
    fun `the annotated key is the only one that resolves`() {
        // The drain response spells it subId, but PushService reads that path
        // with raw JSON. Through Gson only the wire spelling counts, so this
        // pins which of the two envelopes this model is for.
        val camel = gson.fromJson("""{"subId":"s-7f3c"}""", WebSocketEvent::class.java)
        assertEquals(null, camel.subId)
    }

    @Test
    fun `an unrelated event leaves the push fields null`() {
        val event = gson.fromJson("""{"type":"post/create","feed":"f1"}""", WebSocketEvent::class.java)
        assertNotNull(event)
        assertEquals(null, event.subId)
        assertEquals(null, event.payload)
        assertEquals(null, event.account)
    }

    @Test
    fun `the object key still reaches objectId`() {
        // Guards the sibling annotation this one was modelled on.
        val event = gson.fromJson("""{"type":"clear_object","object":"o1"}""", WebSocketEvent::class.java)
        assertEquals("o1", event.objectId)
    }
}
