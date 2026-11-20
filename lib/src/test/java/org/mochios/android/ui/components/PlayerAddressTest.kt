// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.OWNED
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A player is handed its address as the app holds it and resolves it onto the
 * server as each request goes, so it needs no server address of its own and
 * follows a change of server; only its own address is marked for a credential.
 */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerAddressTest {
    private val own = "/feeds/1abc/-/attachments/9"

    @Test
    fun `the player's own address is resolved onto the server and marked`() {
        val sent = marked(DataSpec(Uri.parse(own)), own, "https://mochi.example/")
        assertEquals("https://mochi.example/feeds/1abc/-/attachments/9", sent.uri.toString())
        assertEquals("1", sent.httpRequestHeaders[OWNED])
    }

    @Test
    fun `another relative address is resolved onto the server and not marked`() {
        val sent = marked(DataSpec(Uri.parse("/feeds/1abc/-/attachments/segment2.ts")), own, "https://mochi.example")
        assertEquals("https://mochi.example/feeds/1abc/-/attachments/segment2.ts", sent.uri.toString())
        assertNull(sent.httpRequestHeaders[OWNED])
    }

    @Test
    fun `the server read at each request is the one used`() {
        val sent = marked(DataSpec(Uri.parse(own)), own, "https://moved.example")
        assertEquals("https://moved.example/feeds/1abc/-/attachments/9", sent.uri.toString())
    }

    @Test
    fun `an absolute address is sent as given and not marked`() {
        val spec = DataSpec(Uri.parse("https://cdn.example/clip.mp4"))
        val sent = marked(spec, own, "https://mochi.example")
        assertEquals("https://cdn.example/clip.mp4", sent.uri.toString())
        assertNull(sent.httpRequestHeaders[OWNED])
    }
}
